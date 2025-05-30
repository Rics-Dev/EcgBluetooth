package dev.atick.compose.ui.dashboard.data

data class DashboardUiState(
    val heartRate: Float = 0.0F,
    val ecgPlotData: EcgPlotData = EcgPlotData(),
    val abnormalEcgPlotData: List<EcgPlotData> = listOf(),
    val recordingState: RecordingState = RecordingState.NotRecording,
    val doctorName: String = "",
    val doctorEmail: String = "",
    val patients: List<Patient> = emptyList(),
    val selectedPatient: Patient? = null,
    val isConnectedToDevice: Boolean = false,
    val availableBluetoothDevices: List<BluetoothDevice> = emptyList()
)
