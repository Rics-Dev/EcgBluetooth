package dev.atick.compose.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.atick.compose.ui.dashboard.data.BluetoothDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled ?: false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val availableDevices: StateFlow<List<BluetoothDevice>> = _availableDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()

    private val _ecgData = MutableStateFlow<List<Int>>(emptyList())
    val ecgData: StateFlow<List<Int>> = _ecgData.asStateFlow()

    init {
        // Register for broadcasts when a device is discovered
        val filter = IntentFilter().apply {
            addAction(android.bluetooth.BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        // context.registerReceiver(bluetoothReceiver, filter)
    }

    fun startScan() {
        if (hasPermission() && bluetoothAdapter?.isEnabled == true) {
            bluetoothAdapter.startDiscovery()

            // For testing, add some dummy devices
            val dummyDevices = listOf(
                BluetoothDevice(name = "ECG Monitor 1", address = "00:11:22:33:44:55", isBonded = true),
                BluetoothDevice(name = "ECG Device 2", address = "AA:BB:CC:DD:EE:FF"),
                BluetoothDevice(name = "Cardiac Monitor", address = "12:34:56:78:90:AB")
            )
            _availableDevices.value = dummyDevices
        }
    }

    fun stopScan() {
        if (hasPermission() && bluetoothAdapter?.isEnabled == true) {
            bluetoothAdapter.cancelDiscovery()
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        // In a real app, implement actual Bluetooth connection logic here
        _connectedDevice.value = device

        // Simulate connection success
        simulateEcgData()
    }

    fun disconnectDevice() {
        _connectedDevice.value = null
    }

    // For demo purposes - simulate ECG data
    private fun simulateEcgData() {
        // In a real app, you would receive actual ECG data from the connected device
        _ecgData.value = List(300) { (Math.sin(it * 0.05) * 100).toInt() }
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
    }
}
