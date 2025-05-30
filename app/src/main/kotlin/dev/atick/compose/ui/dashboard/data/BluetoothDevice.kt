package dev.atick.compose.ui.dashboard.data

/**
 * Represents a Bluetooth device in the application
 */
data class BluetoothDevice(
    val name: String? = null,
    val address: String,
    val rssi: Int = -100, // Signal strength (more negative = weaker signal)
    val isBonded: Boolean = false // Whether the device is paired/bonded
)
