package dev.atick.compose.ui.dashboard.data

data class Patient(
    val id: String = "",
    val name: String = "",
    val age: Int = 0,
    val gender: String = "",
    val medicalHistory: String = "",
    val lastRecorded: Long = 0L
)
