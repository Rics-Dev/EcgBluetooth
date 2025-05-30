package dev.atick.compose.ui.dashboard.data

sealed class RecordingState(val description: String) {
    object Recording : RecordingState("STOP RECORDING")
    object NotRecording : RecordingState("RECORD ECG")
    object PatientRecording : RecordingState("STOP PATIENT RECORDING")
    object ReadyToRecord : RecordingState("START RECORDING")
}

