package dev.atick.compose.ui.dashboard

import android.os.Environment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineDataSet
import com.orhanobut.logger.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.atick.compose.auth.AppwriteAuthService
import dev.atick.compose.bluetooth.BluetoothManager
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
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val bluetoothManager: BluetoothManager,
    private val userPreferences: UserPreferences,
    private val cardiacZoneRepository: CardiacZoneRepository,
    private val appwriteAuthService: AppwriteAuthService
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

    init {
        viewModelScope.launch {
            bluetoothManager.ecgData.collect { ecgData ->
                ecgBuffer.clear()
                ecgBuffer.addAll(ecgData)
                _uiState.update { state ->
                    state.copy(
                        ecgPlotData = getEcgPlotData(ecgBuffer),
                        heartRate = calculateHeartRate(ecgData)
                    )
                }
                updateRecording(ecgData)
            }
        }

        viewModelScope.launch {
            bluetoothManager.availableDevices.collect { devices ->
                _uiState.update { state ->
                    state.copy(availableBluetoothDevices = devices)
                }
            }
        }

        viewModelScope.launch {
            bluetoothManager.connectedDevice.collect { device ->
                _uiState.update { state ->
                    state.copy(isConnectedToDevice = device != null)
                }
            }
        }

        viewModelScope.launch {
            cardiacZoneRepository.abnormalEcg.collect { abnormalEcgList ->
                _uiState.update { state ->
                    state.copy(
                        abnormalEcgPlotData = abnormalEcgList.toEcgPlotData()
                    )
                }
            }
        }

        viewModelScope.launch {
            userPreferences.getUserId().collect { id ->
                Logger.w("USER ID: $id")
                doctorId = id
                loadDoctorInfo()
            }
        }

        // Load dummy patients for testing
        loadDummyPatients()
    }

    private fun loadDoctorInfo() {
        viewModelScope.launch {
            // In a real app, fetch this from the repository based on doctorId
            val doctorInfo = appwriteAuthService.getCurrentUser()
            doctorInfo?.let { user ->
                _uiState.update { state ->
                    state.copy(
                        doctorName = user.name ?: "Dr. John Doe",
                        doctorEmail = user.email ?: "doctor@example.com"
                    )
                }
            }
        }
    }

    private fun loadDummyPatients() {
        val dummyPatients = listOf(
            Patient(
                id = "1",
                name = "Alice Smith",
                age = 45,

