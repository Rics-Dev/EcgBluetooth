package dev.atick.compose.ui.dashboard.data

data class BluetoothDevice(
    val name: String = "Unknown Device",
    val address: String = "",
    val deviceClass: Int = 0,
    val isBonded: Boolean = false
)
