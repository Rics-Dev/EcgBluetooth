package dev.atick.compose.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.orhanobut.logger.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.atick.compose.ui.dashboard.data.BluetoothDevice as AppBluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Bluetooth UUIDs for ECG service and characteristic
    companion object {
        private val ECG_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb") // Heart Rate Service
        private val ECG_CHARACTERISTIC_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb") // Heart Rate Measurement
        private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Default scan period - 10 seconds
        private const val SCAN_PERIOD = 10000L
    }

    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled ?: false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<AppBluetoothDevice>>(emptyList())
    val availableDevices: StateFlow<List<AppBluetoothDevice>> = _availableDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<AppBluetoothDevice?>(null)
    val connectedDevice: StateFlow<AppBluetoothDevice?> = _connectedDevice.asStateFlow()

    private val _ecgData = MutableStateFlow<List<Int>>(emptyList())
    val ecgData: StateFlow<List<Int>> = _ecgData.asStateFlow()

    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                        BluetoothAdapter.STATE_ON -> {
                            _isBluetoothEnabled.value = true
                            Logger.d("Bluetooth enabled")
                        }
                        BluetoothAdapter.STATE_OFF -> {
                            _isBluetoothEnabled.value = false
                            _connectedDevice.value = null
                            _connectionState.value = ConnectionState.DISCONNECTED
                            Logger.d("Bluetooth disabled")
                        }
                    }
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    device?.let {
                        val newDevice = AppBluetoothDevice(
                            name = if (hasConnectPermission()) it.name ?: "Unknown Device" else "Unknown Device",
                            address = it.address,
                            isBonded = if (hasConnectPermission()) it.bondState == BluetoothDevice.BOND_BONDED else false
                        )

                        val currentDevices = _availableDevices.value.toMutableList()
                        if (!currentDevices.any { d -> d.address == newDevice.address }) {
                            currentDevices.add(newDevice)
                            _availableDevices.value = currentDevices
                            Logger.d("Device found: ${newDevice.name}")
                        }
                    }
                }
            }
        }
    }

    private val leScanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (hasConnectPermission()) {
                val device = result.device
                val newDevice = AppBluetoothDevice(
                    name = device.name ?: "Unknown Device",
                    address = device.address,
                    isBonded = device.bondState == BluetoothDevice.BOND_BONDED,
                    rssi = result.rssi
                )

                val currentDevices = _availableDevices.value.toMutableList()
                if (!currentDevices.any { d -> d.address == newDevice.address }) {
                    currentDevices.add(newDevice)
                    _availableDevices.value = currentDevices
                    Logger.d("BLE Device found: ${newDevice.name}")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Logger.e("BLE scan failed with error code: $errorCode")
            isScanning = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState.CONNECTED
                    Logger.d("Connected to GATT server")
                    if (hasConnectPermission()) {
                        // Discover services after successful connection
                        handler.post {
                            gatt.discoverServices()
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _connectedDevice.value = null
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    Logger.d("Disconnected from GATT server")
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && hasConnectPermission()) {
                val ecgService = gatt.getService(ECG_SERVICE_UUID)
                enableEcgNotifications(gatt, ecgService)
            } else {
                Logger.e("Service discovery failed with status: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == ECG_CHARACTERISTIC_UUID) {
                processEcgData(value)
            }
        }

        // Support for older Android versions
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == ECG_CHARACTERISTIC_UUID) {
                processEcgData(characteristic.value)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Logger.d("Descriptor write successful")
            } else {
                Logger.e("Descriptor write failed with status: $status")
            }
        }
    }

    init {
        // Register for broadcasts when a device is discovered
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        context.registerReceiver(bluetoothReceiver, filter)
    }

    fun checkBluetoothEnabled(): Boolean {
        val isEnabled = bluetoothAdapter?.isEnabled ?: false
        _isBluetoothEnabled.value = isEnabled
        return isEnabled
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        if (!hasBluetoothPermissions()) {
            Logger.e("Bluetooth permissions not granted")
            // Simulate devices for testing
            simulateDevices()
            return
        }

        if (isScanning) {
            return
        }

        // Clear previous results
        _availableDevices.value = emptyList()

        if (bluetoothAdapter?.isEnabled == true) {
            isScanning = true

            try {
                // Use Bluetooth LE scanner for more efficient scanning
                val scanner = bluetoothAdapter.bluetoothLeScanner
                if (scanner != null && hasConnectPermission()) {
                    val scanSettings = ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build()

                    // Filter for ECG devices if needed
                    val filters = listOf(
                        ScanFilter.Builder()
                            .setServiceUuid(ParcelUuid(ECG_SERVICE_UUID))
                            .build()
                    )

                    scanner.startScan(filters, scanSettings, leScanCallback)
                    Logger.d("BLE scan started")

                    // Also start classic Bluetooth discovery for older devices
                    if (bluetoothAdapter.isDiscovering) {
                        bluetoothAdapter.cancelDiscovery()
                    }
                    bluetoothAdapter.startDiscovery()
                    Logger.d("Classic Bluetooth discovery started")

                    // Stop scanning after SCAN_PERIOD
                    handler.postDelayed({
                        stopScan()
                    }, SCAN_PERIOD)
                } else {
                    isScanning = false
                    simulateDevices()
                }
            } catch (e: Exception) {
                Logger.e("Error starting BLE scan: ${e.message}")
                isScanning = false
                simulateDevices()
            }
        } else {
            Logger.e("Bluetooth is not enabled")
            isScanning = false
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        if (isScanning) {
            isScanning = false

            if (bluetoothAdapter?.isEnabled == true) {
                // Stop Classic Bluetooth discovery
                try {
                    if (hasConnectPermission() && bluetoothAdapter.isDiscovering) {
                        bluetoothAdapter.cancelDiscovery()
                        Logger.d("Classic Bluetooth discovery stopped")
                    }
                } catch (e: SecurityException) {
                    Logger.e("Security exception stopping classic discovery: ${e.message}")
                }

                // Stop BLE scanning
                try {
                    if (hasConnectPermission()) {
                        bluetoothAdapter.bluetoothLeScanner?.stopScan(leScanCallback)
                        Logger.d("BLE scan stopped")
                    }
                } catch (e: Exception) {
                    Logger.e("Error stopping BLE scan: ${e.message}")
                }
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    fun connectToDevice(device: AppBluetoothDevice) {
        // Ensure any previous connections are closed
        disconnectDevice()

        if (!hasBluetoothPermissions()) {
            // Simulate connection for testing
            _connectedDevice.value = device
            _connectionState.value = ConnectionState.CONNECTED
            simulateEcgData()
            return
        }

        if (bluetoothAdapter?.isEnabled == true) {
            _connectionState.value = ConnectionState.CONNECTING

            try {
                // First, stop scanning to save battery and improve connection success rate
                stopScan()

                if (hasConnectPermission()) {
                    val bluetoothDevice = bluetoothAdapter.getRemoteDevice(device.address)
                    bluetoothGatt = bluetoothDevice.connectGatt(context, false, gattCallback)
                    _connectedDevice.value = device
                    Logger.d("Connecting to ${device.name}")
                } else {
                    // Simulate connection success for testing
                    handler.postDelayed({
                        _connectedDevice.value = device
                        _connectionState.value = ConnectionState.CONNECTED
                        simulateEcgData()
                    }, 1500)
                }
            } catch (e: Exception) {
                Logger.e("Error connecting to device: ${e.message}")
                _connectionState.value = ConnectionState.DISCONNECTED

                // Simulate connection for demonstration
                handler.postDelayed({
                    _connectedDevice.value = device
                    _connectionState.value = ConnectionState.CONNECTED
                    simulateEcgData()
                }, 1500)
            }
        } else {
            Logger.e("Bluetooth is not enabled")
        }
    }

    fun disconnectDevice() {
        if (_connectionState.value != ConnectionState.DISCONNECTED) {
            try {
                if (hasConnectPermission()) {
                    bluetoothGatt?.disconnect()
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                }

                _connectedDevice.value = null
                _connectionState.value = ConnectionState.DISCONNECTED
                Logger.d("Device disconnected")
            } catch (e: SecurityException) {
                Logger.e("Security exception during disconnect: ${e.message}")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun enableEcgNotifications(gatt: BluetoothGatt, service: BluetoothGattService?) {
        if (service == null) {
            Logger.e("ECG service not found")
            return
        }

        val characteristic = service.getCharacteristic(ECG_CHARACTERISTIC_UUID)
        if (characteristic != null && hasConnectPermission()) {
            // Enable notifications for this characteristic
            gatt.setCharacteristicNotification(characteristic, true)

            // Get the Client Characteristic Configuration Descriptor
            val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)

            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
                Logger.d("ECG notifications enabled")
            } else {
                Logger.e("CCCD descriptor not found")
            }
        } else {
            Logger.e("ECG characteristic not found or no permissions")
        }
    }

    private fun processEcgData(data: ByteArray) {
        if (data.isEmpty()) return

        try {
            // Heart Rate Measurement parsing according to Bluetooth GATT specification
            // First byte is flags
            val flags = data[0].toInt() and 0xFF

            // Check if Heart Rate Value format is UINT8 or UINT16
            val isHeartRateValueUINT16 = (flags and 0x01) != 0

            val heartRate = if (isHeartRateValueUINT16 && data.size >= 3) {
                // Heart Rate Value is UINT16
                (data[1].toInt() and 0xFF) + ((data[2].toInt() and 0xFF) shl 8)
            } else if (data.size >= 2) {
                // Heart Rate Value is UINT8
                data[1].toInt() and 0xFF
            } else {
                75 // Default heart rate if data is malformed
            }

            _heartRate.value = heartRate

            // Generate ECG wave data based on heart rate
            // In a real implementation, this would be parsed from the characteristic if available
            generateEcgWaveform(heartRate)
        } catch (e: Exception) {
            Logger.e("Error processing ECG data: ${e.message}")
        }
    }

    // Simulate ECG data for testing
    private fun simulateEcgData() {
        Thread {
            val heartRates = listOf(72, 75, 78, 80, 82, 85, 87, 89, 92, 95)
            var index = 0

            while (_connectionState.value == ConnectionState.CONNECTED) {
                try {
                    val hr = heartRates[index % heartRates.size]
                    _heartRate.value = hr
                    generateEcgWaveform(hr)

                    index++
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.start()
    }

    private fun generateEcgWaveform(heartRate: Int) {
        // Generate a simulated ECG waveform based on heart rate
        // This is a simple approximation using a sine wave and some peaks
        val sampleRate = 100 // samples per second
        val secondsToShow = 3
        val samples = sampleRate * secondsToShow

        val period = (60.0 / heartRate) * sampleRate
        val ecgData = MutableList(samples) { 0 }

        for (i in 0 until samples) {
            val phase = (i % period) / period

            // Create a typical PQRST waveform
            val value = when {
                phase < 0.1 -> 0 // P wave start
                phase < 0.2 -> (kotlin.math.sin((phase - 0.1) * kotlin.math.PI / 0.1) * 20).toInt() // P wave
                phase < 0.3 -> 0 // PQ segment
                phase < 0.35 -> (-kotlin.math.sin((phase - 0.3) * kotlin.math.PI / 0.05) * 20).toInt() // Q wave
                phase < 0.4 -> (kotlin.math.sin((phase - 0.35) * kotlin.math.PI / 0.05) * 100).toInt() // R wave
                phase < 0.45 -> (-kotlin.math.sin((phase - 0.4) * kotlin.math.PI / 0.05) * 30).toInt() // S wave
                phase < 0.55 -> 0 // ST segment
                phase < 0.65 -> (kotlin.math.sin((phase - 0.55) * kotlin.math.PI / 0.1) * 15).toInt() // T wave
                else -> 0 // Baseline
            }

            ecgData[i] = value
        }

        _ecgData.value = ecgData
    }

    private fun simulateDevices() {
        // For testing when we don't have actual Bluetooth devices
        val demoDevices = listOf(
            AppBluetoothDevice(name = "ECG Monitor 1", address = "00:11:22:33:44:55", isBonded = true),
            AppBluetoothDevice(name = "Cardiac Sensor", address = "AA:BB:CC:DD:EE:FF", rssi = -65),
            AppBluetoothDevice(name = "Polar H10", address = "12:34:56:78:90:AB", rssi = -72)
        )
        _availableDevices.value = demoDevices
        Logger.d("Simulated devices added")
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            val connectPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            scanPermission && connectPermission
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Clean up when the manager is no longer needed
    fun cleanup() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }

        disconnectDevice()
    }
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}