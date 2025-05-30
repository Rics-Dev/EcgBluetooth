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
import dev.atick.compose.services.EcgRecord
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
    private var recordingSampleCount = 0

    init {
        initializeFlows()
        loadDoctorInfo()
    }

    private fun initializeFlows() {
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
        viewModelScope.launch {
            try {
                // Wait for valid doctor ID from preferences
                userPreferences.getUserId().collect { id ->
                    if (id.isNotEmpty() && id != "-1") {
                        doctorId = id
                        Logger.d("Loading patients for doctor: $doctorId")

                        val patients = patientService.getPatientsByDoctor(doctorId)

                        if (patients.isNotEmpty()) {
                            Logger.d("Loaded ${patients.size} patients from Appwrite")
                            // Patients are automatically updated via the StateFlow in PatientService
                        } else {
                            Logger.d("No patients found in Appwrite for doctor: $doctorId")
                            // Don't load dummy patients - keep empty state
                            _uiState.update { state ->
                                state.copy(patients = emptyList())
                            }
                        }
                    } else {
                        Logger.w("Invalid doctor ID: $id")
                        // Handle authentication issue
                        _patientAddedEvent.postValue(Event("Please log in again"))
                    }
                }
            } catch (e: Exception) {
                Logger.e("Failed to load patients: ${e.message}")
                _patientAddedEvent.postValue(Event("Failed to load patients: ${e.message}"))
            }
        }
    }

    fun addPatient(patient: Patient) {
        viewModelScope.launch {
            try {
                if (doctorId.isNotEmpty() && doctorId != "-1") {
                    // Save to Appwrite
                    Logger.d("Adding patient to Appwrite: ${patient.name} for doctor: $doctorId")
                    val savedPatient = patientService.createPatient(patient, doctorId)
                    if (savedPatient != null) {
                        _patientAddedEvent.postValue(Event("Patient ${patient.name} added successfully"))
                        Logger.d("Patient added to Appwrite: ${patient.name}")

                        // Refresh the patient list
                        loadPatientsFromAppwrite()
                    } else {
                        _patientAddedEvent.postValue(Event("Failed to add patient to database"))
                    }
                } else {
                    _patientAddedEvent.postValue(Event("Please log in to add patients"))
                    Logger.e("Cannot add patient: Invalid doctor ID")
                }
            } catch (e: Exception) {
                Logger.e("Failed to add patient: ${e.message}")
                _patientAddedEvent.postValue(Event("Failed to add patient: ${e.message}"))
            }
        }
    }

    // Add a specific method to load from Appwrite
    private suspend fun loadPatientsFromAppwrite() {
        try {
            val patients = patientService.getPatientsByDoctor(doctorId)
            Logger.d("Refreshed patients from Appwrite: ${patients.size} patients")
        } catch (e: Exception) {
            Logger.e("Failed to refresh patients: ${e.message}")
        }
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

    private fun updateRecording(ecgData: List<Int>) {
        if (_uiState.value.recordingState == RecordingState.Recording) {
            recording.addAll(ecgData)
            recordingSampleCount += ecgData.size

            // Log recording progress
            val durationMs = System.currentTimeMillis() - recordingStartTime
            val durationSeconds = durationMs / 1000.0
            Logger.d("Recording: ${recording.size} samples, ${durationSeconds}s, Rate: ${recordingSampleCount / durationSeconds} Hz")
        }
    }

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

        // Clear previous recording data
        recording.clear()
        recordingSampleCount = 0
        recordingStartTime = System.currentTimeMillis()

        Logger.d("Started ECG recording for patient: ${selectedPatient.name}")
        _patientAddedEvent.postValue(Event("Started recording ECG for ${selectedPatient.name}"))
    }

    fun stopRecording() {
        val recordingDuration = System.currentTimeMillis() - recordingStartTime

        _uiState.update { state ->
            state.copy(recordingState = RecordingState.NotRecording)
        }

        if (recording.isNotEmpty()) {
            Logger.d("Stopped ECG recording - Duration: ${recordingDuration}ms, Samples: ${recording.size}")
            saveRecording()
        } else {
            Logger.w("No ECG data recorded")
            _patientAddedEvent.postValue(Event("No ECG data was recorded"))
        }
    }

    private fun saveRecording() {
        val selectedPatient = _uiState.value.selectedPatient
        if (selectedPatient == null) {
            Logger.e("No patient selected for saving ECG recording")
            _patientAddedEvent.postValue(Event("No patient selected"))
            return
        }

        if (recording.isEmpty()) {
            Logger.e("No ECG data to save")
            _patientAddedEvent.postValue(Event("No ECG data recorded"))
            return
        }

        val recordingDuration = System.currentTimeMillis() - recordingStartTime
        val sampleRate = recording.size.toFloat() / (recordingDuration / 1000.0f)

        Logger.d("Saving ECG recording:")
        Logger.d("- Patient: ${selectedPatient.name}")
        Logger.d("- Duration: ${recordingDuration}ms")
        Logger.d("- Samples: ${recording.size}")
        Logger.d("- Sample Rate: ${sampleRate} Hz")
        Logger.d("- Heart Rate: ${_uiState.value.heartRate} BPM")

        viewModelScope.launch {
            try {
                if (doctorId.isNotEmpty() && doctorId != "-1" && !selectedPatient.id.startsWith("demo_")) {
                    Logger.d("Attempting to save ECG record to Appwrite for patient: ${selectedPatient.id}")

                    // Validate recording data
                    if (recordingDuration < 1000) { // Less than 1 second
                        Logger.w("Recording duration too short: ${recordingDuration}ms")
                        _patientAddedEvent.postValue(Event("Recording too short - minimum 1 second required"))
                        return@launch
                    }

                    if (recording.size < 10) { // Less than 10 samples
                        Logger.w("Recording has too few samples: ${recording.size}")
                        _patientAddedEvent.postValue(Event("Recording has insufficient data"))
                        return@launch
                    }

                    // Detect if recording might be abnormal (basic analysis)
                    val isAbnormal = detectAbnormalEcg(recording.toList(), _uiState.value.heartRate)

                    val recordId = patientService.saveEcgRecord(
                        patientId = selectedPatient.id,
                        ecgData = recording.toList(),
                        heartRate = _uiState.value.heartRate,
                        recordingDuration = recordingDuration,
                        isAbnormal = isAbnormal
                    )

                    if (recordId != null) {
                        Logger.d("ECG record saved successfully to Appwrite with ID: $recordId")
                        val message = if (isAbnormal) {
                            "ECG recording saved - ABNORMAL PATTERNS DETECTED!"
                        } else {
                            "ECG recording saved successfully"
                        }
                        _patientAddedEvent.postValue(Event(message))

                        // Update patient's last recorded time
                        val updatedPatient = selectedPatient.copy(lastRecorded = System.currentTimeMillis())
                        updatePatient(updatedPatient)

                        // Refresh ECG records for the patient
                        loadPatientEcgRecords(selectedPatient.id)

                        return@launch
                    } else {
                        Logger.e("Failed to save ECG record to Appwrite - recordId is null")
                        _patientAddedEvent.postValue(Event("Failed to save ECG recording to cloud"))
                    }
                } else {
                    Logger.w("Cannot save to Appwrite - DoctorId: '$doctorId', PatientId: '${selectedPatient.id}'")
                    _patientAddedEvent.postValue(Event("Cannot save to cloud - invalid session"))
                }

                Logger.d("Falling back to local storage")
                saveRecordingLocally()

            } catch (e: Exception) {
                Logger.e("Exception while saving ECG recording: ${e.message}")
                e.printStackTrace()
                _patientAddedEvent.postValue(Event("Error saving ECG: ${e.message}"))

                try {
                    saveRecordingLocally()
                } catch (localException: Exception) {
                    Logger.e("Failed to save locally as well: ${localException.message}")
                    _patientAddedEvent.postValue(Event("Failed to save ECG recording"))
                }
            }
        }
    }

    // Basic abnormal ECG detection
    private fun detectAbnormalEcg(ecgData: List<Int>, heartRate: Float): Boolean {
        return try {
            // Heart rate based detection
            if (heartRate < 40 || heartRate > 150) {
                Logger.d("Abnormal heart rate detected: $heartRate BPM")
                return true
            }

            // Statistical analysis of ECG data
            val mean = ecgData.average()
            val variance = ecgData.map { (it - mean) * (it - mean) }.average()
            val stdDev = kotlin.math.sqrt(variance)

            // Check for flat line (very low variance)
            if (stdDev < 5) {
                Logger.d("Flat ECG signal detected (std dev: $stdDev)")
                return true
            }

            // Check for extreme values (artifacts)
            val extremeValueCount = ecgData.count { kotlin.math.abs(it - mean) > 4 * stdDev }
            val extremeValuePercentage = extremeValueCount.toDouble() / ecgData.size

            if (extremeValuePercentage > 0.05) { // More than 5% extreme values
                Logger.d("Too many extreme values detected: ${extremeValuePercentage * 100}%")
                return true
            }

            // Detect R-peaks and analyze rhythm
            val rPeaks = detectRPeaks(ecgData)
            if (rPeaks.isNotEmpty()) {
                val rrIntervals = calculateRRIntervals(rPeaks, 100.0) // Assuming 100 Hz sample rate
                val rrVariability = calculateRRVariability(rrIntervals)

                // High RR variability might indicate arrhythmia
                if (rrVariability > 0.3) { // 30% variability threshold
                    Logger.d("High RR variability detected: ${rrVariability * 100}%")
                    return true
                }

                // Check for irregular rhythm
                val avgRR = rrIntervals.average()
                val irregularBeats = rrIntervals.count { kotlin.math.abs(it - avgRR) > avgRR * 0.2 }
                val irregularPercentage = irregularBeats.toDouble() / rrIntervals.size

                if (irregularPercentage > 0.1) { // More than 10% irregular beats
                    Logger.d("Irregular rhythm detected: ${irregularPercentage * 100}% irregular beats")
                    return true
                }
            }

            Logger.d("ECG appears normal based on basic analysis")
            false

        } catch (e: Exception) {
            Logger.e("Error during ECG analysis: ${e.message}")
            false // Default to normal if analysis fails
        }
    }

    private fun calculateRRIntervals(rPeaks: List<Int>, sampleRate: Double): List<Double> {
        if (rPeaks.size < 2) return emptyList()

        return rPeaks.zipWithNext { current, next ->
            (next - current) / sampleRate // Convert to seconds
        }
    }

    private fun calculateRRVariability(rrIntervals: List<Double>): Double {
        if (rrIntervals.isEmpty()) return 0.0

        val mean = rrIntervals.average()
        val variance = rrIntervals.map { (it - mean) * (it - mean) }.average()
        val stdDev = kotlin.math.sqrt(variance)

        return if (mean > 0) stdDev / mean else 0.0
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

    // Add this method to DashboardViewModel
    fun refreshPatients() {
        viewModelScope.launch {
            try {
                if (doctorId.isNotEmpty() && doctorId != "-1") {
                    Logger.d("Refreshing patients for doctor: $doctorId")
                    patientService.getPatientsByDoctor(doctorId)
                }
            } catch (e: Exception) {
                Logger.e("Failed to refresh patients: ${e.message}")
                _patientAddedEvent.postValue(Event("Failed to refresh patients"))
            }
        }
    }

    fun loadPatientEcgRecords(patientId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { state ->
                    state.copy(isLoadingRecords = true)
                }

                Logger.d("Loading ECG records for patient: $patientId")
                val records = patientService.getEcgRecords(patientId)

                _uiState.update { state ->
                    state.copy(
                        patientEcgRecords = records,
                        isLoadingRecords = false
                    )
                }

                Logger.d("Loaded ${records.size} ECG records for patient")

            } catch (e: Exception) {
                Logger.e("Failed to load ECG records: ${e.message}")
                _uiState.update { state ->
                    state.copy(isLoadingRecords = false)
                }
                _patientAddedEvent.postValue(Event("Failed to load ECG records: ${e.message}"))
            }
        }
    }

    fun selectPatient(patient: Patient) {
        _uiState.update { state ->
            state.copy(selectedPatient = patient)
        }
        Logger.d("Selected patient: ${patient.name}")

        // Automatically load ECG records when a patient is selected
        if (patient.id.isNotEmpty() && !patient.id.startsWith("demo_")) {
            loadPatientEcgRecords(patient.id)
        }
    }

    fun deleteEcgRecord(recordId: String) {
        viewModelScope.launch {
            try {
                val success = patientService.deleteEcgRecord(recordId)
                if (success) {
                    // Refresh the records list
                    _uiState.value.selectedPatient?.let { patient ->
                        loadPatientEcgRecords(patient.id)
                    }
                    _patientAddedEvent.postValue(Event("ECG record deleted successfully"))
                } else {
                    _patientAddedEvent.postValue(Event("Failed to delete ECG record"))
                }
            } catch (e: Exception) {
                Logger.e("Failed to delete ECG record: ${e.message}")
                _patientAddedEvent.postValue(Event("Error deleting ECG record: ${e.message}"))
            }
        }
    }

    // Add this method to DashboardViewModel.kt to handle viewing ECG records
    fun viewEcgRecord(record: EcgRecord) {
        viewModelScope.launch {
            try {
                // Convert EcgRecord to EcgPlotData for visualization
                val ecgPlotData = record.toEcgPlotData()

                // You can either:
                // 1. Add it to abnormalEcgPlotData for display in existing cards
                // 2. Create a new state for viewing individual records
                // 3. Navigate to a detailed view screen

                // For now, let's add it to a viewing state
                _uiState.update { state ->
                    state.copy(
                        // Add a new field for viewing records or use existing abnormalEcgPlotData
                        abnormalEcgPlotData = listOf(ecgPlotData)
                    )
                }

                _patientAddedEvent.postValue(Event("Viewing ECG record from ${formatTimestamp(record.timestamp)}"))

            } catch (e: Exception) {
                Logger.e("Failed to view ECG record: ${e.message}")
                _patientAddedEvent.postValue(Event("Failed to load ECG record"))
            }
        }
    }

    // Extension function to convert EcgRecord to EcgPlotData
    private fun EcgRecord.toEcgPlotData(): EcgPlotData {
        val entries = ecgData.mapIndexed { index, value ->
            Entry(index.toFloat(), value.toFloat())
        }

        val lineDataSet = LineDataSet(entries, "ECG Recording")

        // Detect R-peaks for visualization
        val rPeaks = detectRPeaks(ecgData)
        val rPeakEntries = rPeaks.map { index ->
            Entry(index.toFloat(), ecgData[index].toFloat())
        }

        return EcgPlotData(
            id = timestamp,
            ecg = lineDataSet,
            rPeaks = ScatterDataSet(rPeakEntries, "R-PEAK"),
            vBeats = ScatterDataSet(emptyList(), "V-BEAT"),
            sBeats = ScatterDataSet(emptyList(), "S-BEAT"),
            af = if (isAbnormal) 1 else 0
        )
    }

    private fun formatTimestamp(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            "Unknown time"
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up Bluetooth manager when ViewModel is destroyed
        bluetoothManager.cleanup()
    }
}
