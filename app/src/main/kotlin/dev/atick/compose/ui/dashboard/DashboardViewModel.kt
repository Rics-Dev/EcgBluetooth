package dev.atick.compose.ui.dashboard

import android.Manifest
import android.os.Environment
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.ScatterDataSet
import com.orhanobut.logger.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.atick.compose.auth.AppwriteAuthService
import dev.atick.compose.bluetooth.BluetoothManager
import dev.atick.compose.services.PatientService
import dev.atick.compose.ui.dashboard.data.*
import dev.atick.core.utils.Event
import dev.atick.network.data.ConnectDoctorRequest
import dev.atick.network.repository.CardiacZoneRepository
import dev.atick.storage.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val bluetoothManager: BluetoothManager,
    private val userPreferences: UserPreferences,
    private val cardiacZoneRepository: CardiacZoneRepository,
    private val appwriteAuthService: AppwriteAuthService,
    private val patientService: PatientService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState>
        get() = _uiState

    private val _patientAddedEvent = MutableLiveData<Event<String>>()
    val patientAddedEvent: LiveData<Event<String>>
        get() = _patientAddedEvent

    private val _connectDoctorStatus = MutableLiveData<Event<String>>()
    val connectDoctorStatus: LiveData<Event<String>>
        get() = _connectDoctorStatus

    private val ecgBuffer = MutableList(300) { 0 }
    private val recording = mutableListOf<Int>()
    private var doctorId = "-1"
    private var recordingStartTime = 0L

    init {
        initializeFlows()
        loadDoctorInfo()
    }

    private fun initializeFlows() {
        // Monitor ECG data from Enhanced Bluetooth manager
        viewModelScope.launch {
            bluetoothManager.ecgData.collect { ecgData ->
                if (ecgData.isNotEmpty()) {
                    ecgBuffer.clear()
                    ecgBuffer.addAll(ecgData)
                    _uiState.update { state ->
                        state.copy(ecgPlotData = getEcgPlotData(ecgBuffer))
                    }
                    updateRecording(ecgData)
                }
            }
        }

        // Monitor heart rate from Enhanced Bluetooth manager
        viewModelScope.launch {
            bluetoothManager.heartRate.collect { heartRate ->
                _uiState.update { state ->
                    state.copy(heartRate = heartRate.toFloat())
                }
            }
        }

        // Monitor available Bluetooth devices
        viewModelScope.launch {
            bluetoothManager.availableDevices.collect { devices ->
                _uiState.update { state ->
                    state.copy(availableBluetoothDevices = devices)
                }
            }
        }

        // Monitor Bluetooth connection status
        viewModelScope.launch {
            bluetoothManager.connectedDevice.collect { device ->
                _uiState.update { state ->
                    state.copy(isConnectedToDevice = device != null)
                }
            }
        }

        // Monitor connection state changes
        viewModelScope.launch {
            bluetoothManager.connectionState.collect { connectionState ->
                Logger.d("Connection state changed: $connectionState")
                // Handle connection state changes if needed
            }
        }

        // Monitor abnormal ECG data
        viewModelScope.launch {
            cardiacZoneRepository.abnormalEcg.collect { abnormalEcgList ->
                _uiState.update { state ->
                    state.copy(
                        abnormalEcgPlotData = abnormalEcgList.toEcgPlotData()
                    )
                }
            }
        }

        // Monitor patients from PatientService
        viewModelScope.launch {
            patientService.patients.collect { patients ->
                _uiState.update { state ->
                    state.copy(patients = patients)
                }
            }
        }

        // Monitor PatientService errors
        viewModelScope.launch {
            patientService.error.collect { error ->
                error?.let {
                    _patientAddedEvent.postValue(Event("Error: ${it.message}"))
                    patientService.clearError()
                }
            }
        }

        // Load doctor information
        viewModelScope.launch {
            userPreferences.getUserId().collect { id ->
                Logger.w("USER ID: $id")
                doctorId = id
                loadDoctorInfo()
                loadPatients()
            }
        }
    }

    private fun loadDoctorInfo() {
        viewModelScope.launch {
            try {
                val doctorInfo = appwriteAuthService.getCurrentUser()
                doctorInfo?.let { user ->
                    _uiState.update { state ->
                        state.copy(
                            doctorName = user.name ?: "Dr. John Doe",
                            doctorEmail = user.email ?: "doctor@example.com"
                        )
                    }
                }
            } catch (e: Exception) {
                Logger.e("Failed to load doctor info: ${e.message}")
                // Set default values if loading fails
                _uiState.update { state ->
                    state.copy(
                        doctorName = "Dr. John Doe",
                        doctorEmail = "doctor@example.com"
                    )
                }
            }
        }
    }

    private fun loadPatients() {
        if (doctorId == "-1" || doctorId.isEmpty()) {
            Logger.w("No valid doctor ID, loading demo patients")
            loadDummyPatients()
            return
        }

        viewModelScope.launch {
            try {
                val patients = patientService.getPatientsByDoctor(doctorId)
                if (patients.isEmpty()) {
                    Logger.d("No patients found, loading demo patients")
                    loadDummyPatients()
                }
            } catch (e: Exception) {
                Logger.e("Failed to load patients: ${e.message}")
                loadDummyPatients()
            }
        }
    }

    private fun loadDummyPatients() {
        val dummyPatients = listOf(
            Patient(
                id = "demo_1",
                name = "Alice Smith",
                age = 45,
                gender = "Female",
                medicalHistory = "Hypertension, Diabetes Type 2",
                lastRecorded = System.currentTimeMillis() - 86400000 // 1 day ago
            ),
            Patient(
                id = "demo_2",
                name = "Bob Johnson",
                age = 62,
                gender = "Male",
                medicalHistory = "Previous MI (2019), High cholesterol, Smoker",
                lastRecorded = System.currentTimeMillis() - 172800000 // 2 days ago
            ),
            Patient(
                id = "demo_3",
                name = "Carol Wilson",
                age = 38,
                gender = "Female",
                medicalHistory = "Atrial fibrillation, Anxiety disorder",
                lastRecorded = System.currentTimeMillis() - 259200000 // 3 days ago
            ),
            Patient(
                id = "demo_4",
                name = "David Brown",
                age = 55,
                gender = "Male",
                medicalHistory = "Hypertension, Sleep apnea",
                lastRecorded = System.currentTimeMillis() - 345600000 // 4 days ago
            )
        )
        _uiState.update { state ->
            state.copy(patients = dummyPatients)
        }
    }

    fun addPatient(patient: Patient) {
        viewModelScope.launch {
            try {
                if (doctorId != "-1" && doctorId.isNotEmpty()) {
                    // Save to Appwrite
                    val savedPatient = patientService.createPatient(patient, doctorId)
                    if (savedPatient != null) {
                        _patientAddedEvent.postValue(Event("Patient ${patient.name} added successfully"))
                        Logger.d("Patient added: ${patient.name}")
                    } else {
                        _patientAddedEvent.postValue(Event("Failed to add patient"))
                    }
                } else {
                    // Add to local demo list
                    _uiState.update { state ->
                        state.copy(
                            patients = state.patients + patient
                        )
                    }
                    _patientAddedEvent.postValue(Event("Patient ${patient.name} added locally"))
                }
            } catch (e: Exception) {
                Logger.e("Failed to add patient: ${e.message}")
                _patientAddedEvent.postValue(Event("Failed to add patient: ${e.message}"))
            }
        }
    }

    fun selectPatient(patient: Patient) {
        _uiState.update { state ->
            state.copy(selectedPatient = patient)
        }
        Logger.d("Selected patient: ${patient.name}")
    }

    fun updatePatient(patient: Patient) {
        viewModelScope.launch {
            try {
                if (doctorId != "-1" && doctorId.isNotEmpty() && !patient.id.startsWith("demo_")) {
                    val updatedPatient = patientService.updatePatient(patient)
                    if (updatedPatient != null) {
                        _patientAddedEvent.postValue(Event("Patient ${patient.name} updated successfully"))
                    }
                } else {
                    // Update local demo list
                    _uiState.update { state ->
                        val updatedPatients = state.patients.map {
                            if (it.id == patient.id) patient else it
                        }
                        state.copy(patients = updatedPatients)
                    }
                }
            } catch (e: Exception) {
                Logger.e("Failed to update patient: ${e.message}")
            }
        }
    }

    fun deletePatient(patient: Patient) {
        viewModelScope.launch {
            try {
                if (doctorId != "-1" && doctorId.isNotEmpty() && !patient.id.startsWith("demo_")) {
                    val success = patientService.deletePatient(patient.id)
                    if (success) {
                        _patientAddedEvent.postValue(Event("Patient ${patient.name} deleted successfully"))
                    }
                } else {
                    // Remove from local demo list
                    _uiState.update { state ->
                        state.copy(
                            patients = state.patients.filter { it.id != patient.id },
                            selectedPatient = if (state.selectedPatient?.id == patient.id) null else state.selectedPatient
                        )
                    }
                }
            } catch (e: Exception) {
                Logger.e("Failed to delete patient: ${e.message}")
            }
        }
    }

    // Bluetooth Management Functions
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun scanForDevices() {
        Logger.d("Starting Bluetooth scan for devices")
        bluetoothManager.startScan()
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    fun connectToDevice(device: BluetoothDevice) {
        Logger.d("Connecting to device: ${device.name}")
        bluetoothManager.connectToDevice(device)
    }

    fun disconnectDevice() {
        Logger.d("Disconnecting from device")
        bluetoothManager.disconnectDevice()
    }

    // ECG Recording Functions
    fun startRecording() {
        val selectedPatient = _uiState.value.selectedPatient
        if (selectedPatient == null) {
            Logger.w("No patient selected for recording")
            _patientAddedEvent.postValue(Event("Please select a patient first"))
            return
        }

        _uiState.update { state ->
            state.copy(recordingState = RecordingState.Recording)
        }
        recording.clear()
        recordingStartTime = System.currentTimeMillis()
        Logger.d("Started ECG recording for patient: ${selectedPatient.name}")
    }

    fun stopRecording() {
        _uiState.update { state ->
            state.copy(recordingState = RecordingState.NotRecording)
        }

        if (recording.isNotEmpty()) {
            saveRecording()
        }
        Logger.d("Stopped ECG recording")
    }

    private fun updateRecording(ecgData: List<Int>) {
        if (_uiState.value.recordingState == RecordingState.Recording) {
            recording.addAll(ecgData)
        }
    }

    private fun saveRecording() {
        val selectedPatient = _uiState.value.selectedPatient ?: return
        val recordingDuration = System.currentTimeMillis() - recordingStartTime

        viewModelScope.launch {
            try {
                // Save to Appwrite if possible
                if (doctorId != "-1" && doctorId.isNotEmpty() && !selectedPatient.id.startsWith("demo_")) {
                    val recordId = patientService.saveEcgRecord(
                        patientId = selectedPatient.id,
                        ecgData = recording,
                        heartRate = _uiState.value.heartRate,
                        recordingDuration = recordingDuration
                    )

                    if (recordId != null) {
                        _patientAddedEvent.postValue(Event("ECG recording saved successfully"))
                        // Update patient's last recorded time
                        updatePatient(selectedPatient.copy(lastRecorded = System.currentTimeMillis()))
                    } else {
                        saveRecordingLocally()
                    }
                } else {
                    saveRecordingLocally()
                }
            } catch (e: Exception) {
                Logger.e("Failed to save ECG recording: ${e.message}")
                saveRecordingLocally()
            }
        }
    }

    private suspend fun saveRecordingLocally() {
        withContext(Dispatchers.IO) {
            try {
                val timestamp =
                    SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault()).format(Date())
                val fileName = "ecg_recording_${timestamp}.csv"

                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    fileName
                )

                FileOutputStream(file).use { output ->
                    // Write CSV header
                    output.write("timestamp,ecg_value,patient_id,patient_name,heart_rate\n".toByteArray())

                    // Write ECG data
                    recording.forEachIndexed { index, value ->
                        val line =
                            "${System.currentTimeMillis() + index},$value,${_uiState.value.selectedPatient?.id},${_uiState.value.selectedPatient?.name},${_uiState.value.heartRate}\n"
                        output.write(line.toByteArray())
                    }
                }

                Logger.d("ECG recording saved locally: ${file.absolutePath}")
                _patientAddedEvent.postValue(Event("ECG recording saved locally"))
            } catch (e: IOException) {
                Logger.e("Failed to save ECG recording locally: ${e.message}")
                _patientAddedEvent.postValue(Event("Failed to save ECG recording"))
            }
        }
    }

    fun connectDoctor(doctorId: String) {
        viewModelScope.launch {
            try {
                val selectedPatient = _uiState.value.selectedPatient
                if (selectedPatient == null) {
                    _connectDoctorStatus.postValue(Event("Please select a patient first"))
                    return@launch
                }

                val request = ConnectDoctorRequest(
                    patientId = selectedPatient.id,
                    doctorId = doctorId
                )
                val response = cardiacZoneRepository.connectDoctor(request)

                if (response != null) {
                    _connectDoctorStatus.postValue(Event("Successfully connected to doctor"))
                } else {
                    _connectDoctorStatus.postValue(Event("Failed to connect to doctor"))
                }
            } catch (e: Exception) {
                Logger.e("Failed to connect doctor: ${e.message}")
                _connectDoctorStatus.postValue(Event("Error connecting to doctor: ${e.message}"))
            }
        }
    }

    suspend fun logout() {
        try {
            appwriteAuthService.logout()
            userPreferences.saveUserId("")
            Logger.d("User logged out successfully")
        } catch (e: Exception) {
            Logger.e("Logout failed: ${e.message}")
        }
    }

    // Helper functions for ECG data processing
    private fun getEcgPlotData(ecgData: List<Int>): EcgPlotData {
        val entries = ecgData.mapIndexed { index, value ->
            Entry(index.toFloat(), value.toFloat())
        }

        val lineDataSet = LineDataSet(entries, "ECG")

        // Detect R-peaks (improved algorithm)
        val rPeaks = detectRPeaks(ecgData)
        val rPeakEntries = rPeaks.map { index ->
            Entry(index.toFloat(), ecgData[index].toFloat())
        }

        return EcgPlotData(
            id = System.currentTimeMillis(),
            ecg = lineDataSet,
            rPeaks = ScatterDataSet(rPeakEntries, "R-PEAK"),
            vBeats = ScatterDataSet(emptyList(), "V-BEAT"),
            sBeats = ScatterDataSet(emptyList(), "S-BEAT"),
            af = 0
        )
    }

    private fun detectRPeaks(ecgData: List<Int>): List<Int> {
        if (ecgData.size < 10) return emptyList()

        val peaks = mutableListOf<Int>()
        val smoothedData = smoothEcgData(ecgData)

        // Calculate adaptive threshold
        val maxValue = smoothedData.maxOrNull() ?: 0.0
        val meanValue = smoothedData.average()
        val threshold = meanValue + (maxValue - meanValue) * 0.6

        // Minimum distance between peaks (samples) - approximately 0.6 seconds at 100Hz
        val minPeakDistance = 60

        var lastPeakIndex = -minPeakDistance

        for (i in 2 until smoothedData.size - 2) {
            val current = smoothedData[i]

            // Check if current point is a local maximum and above threshold
            if (current > threshold &&
                current > smoothedData[i - 1] &&
                current > smoothedData[i + 1] &&
                current > smoothedData[i - 2] &&
                current > smoothedData[i + 2] &&
                (i - lastPeakIndex) >= minPeakDistance
            ) {
                peaks.add(i)
                lastPeakIndex = i
            }
        }

        return peaks
    }

    private fun smoothEcgData(ecgData: List<Int>): List<Double> {
        // Apply simple moving average filter for noise reduction
        val windowSize = 5
        val smoothed = mutableListOf<Double>()

        for (i in ecgData.indices) {
            val start = maxOf(0, i - windowSize / 2)
            val end = minOf(ecgData.size - 1, i + windowSize / 2)

            val sum = (start..end).sumOf { ecgData[it].toDouble() }
            val count = end - start + 1
            smoothed.add(sum / count)
        }

        return smoothed
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up Bluetooth manager when ViewModel is destroyed
        bluetoothManager.cleanup()
    }
}
