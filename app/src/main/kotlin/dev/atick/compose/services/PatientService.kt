package dev.atick.compose.services

import android.content.Context
import com.orhanobut.logger.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.atick.compose.constants.AppwriteConfig
import dev.atick.compose.ui.dashboard.data.Patient
import io.appwrite.Client
import io.appwrite.ID
import io.appwrite.exceptions.AppwriteException
import io.appwrite.models.Document
import io.appwrite.services.Databases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = Client(context)
        .setEndpoint(AppwriteConfig.APPWRITE_PUBLIC_ENDPOINT)
        .setProject(AppwriteConfig.APPWRITE_PROJECT_ID)
        .setSelfSigned(true) // For development only, remove in production

    private val databases = Databases(client)

    // Database and Collection IDs
    private val databaseId = "683a229500280acf9e5a"
    private val patientsCollectionId = "683a22a8003d77374076"
    private val ecgRecordsCollectionId = "683a29f2000da2001e81"

    private val _patients = MutableStateFlow<List<Patient>>(emptyList())
    val patients: StateFlow<List<Patient>> = _patients.asStateFlow()

    private val _error = MutableStateFlow<Exception?>(null)
    val error: StateFlow<Exception?> = _error.asStateFlow()

    suspend fun createPatient(patient: Patient, doctorId: String): Patient? {
        return try {
            withContext(Dispatchers.IO) {
                Logger.d("Creating patient: ${patient.name} for doctor: $doctorId")

                val patientData = mapOf(
                    "name" to patient.name,
                    "age" to patient.age,
                    "gender" to patient.gender,
                    "medicalHistory" to patient.medicalHistory,
                    "doctorId" to doctorId,
                    "lastRecorded" to patient.lastRecorded,
                    "createdAt" to System.currentTimeMillis()
                )

                val document = databases.createDocument(
                    databaseId = databaseId,
                    collectionId = patientsCollectionId,
                    documentId = ID.unique(),
                    data = patientData
                )

                val createdPatient = documentToPatient(document)
                Logger.d("Patient created successfully with ID: ${createdPatient.id}")

                // Update local state
                val currentPatients = _patients.value.toMutableList()
                currentPatients.add(createdPatient)
                _patients.value = currentPatients

                createdPatient
            }
        } catch (e: AppwriteException) {
            Logger.e("Appwrite error creating patient: ${e.message}")
            _error.value = e
            null
        } catch (e: Exception) {
            Logger.e("Unexpected error creating patient: ${e.message}")
            _error.value = e
            null
        }
    }

    suspend fun getPatientsByDoctor(doctorId: String): List<Patient> {
        return try {
            withContext(Dispatchers.IO) {
                Logger.d("Fetching patients for doctor: $doctorId")

                val response = databases.listDocuments(
                    databaseId = databaseId,
                    collectionId = patientsCollectionId,
                    queries = listOf(
                        io.appwrite.Query.equal("doctorId", doctorId),
                        io.appwrite.Query.orderDesc("createdAt")
                    )
                )

                val patients = response.documents.mapNotNull { document ->
                    try {
                        documentToPatient(document)
                    } catch (e: Exception) {
                        Logger.e("Failed to parse patient document ${document.id}: ${e.message}")
                        null
                    }
                }

                Logger.d("Successfully fetched ${patients.size} patients from Appwrite")
                _patients.value = patients
                patients
            }
        } catch (e: AppwriteException) {
            Logger.e("Appwrite error fetching patients: ${e.message}")
            _error.value = e
            emptyList()
        } catch (e: Exception) {
            Logger.e("Unexpected error fetching patients: ${e.message}")
            _error.value = e
            emptyList()
        }
    }

    suspend fun updatePatient(patient: Patient): Patient? {
        return try {
            withContext(Dispatchers.IO) {
                Logger.d("Updating patient: ${patient.id}")

                val patientData = mapOf(
                    "name" to patient.name,
                    "age" to patient.age,
                    "gender" to patient.gender,
                    "medicalHistory" to patient.medicalHistory,
                    "lastRecorded" to patient.lastRecorded
                )

                val document = databases.updateDocument(
                    databaseId = databaseId,
                    collectionId = patientsCollectionId,
                    documentId = patient.id,
                    data = patientData
                )

                val updatedPatient = documentToPatient(document)
                Logger.d("Patient updated successfully: ${updatedPatient.id}")

                // Update local state
                val currentPatients = _patients.value.toMutableList()
                val index = currentPatients.indexOfFirst { it.id == patient.id }
                if (index != -1) {
                    currentPatients[index] = updatedPatient
                    _patients.value = currentPatients
                }

                updatedPatient
            }
        } catch (e: AppwriteException) {
            Logger.e("Appwrite error updating patient: ${e.message}")
            _error.value = e
            null
        } catch (e: Exception) {
            Logger.e("Unexpected error updating patient: ${e.message}")
            _error.value = e
            null
        }
    }

    suspend fun deletePatient(patientId: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                Logger.d("Deleting patient: $patientId")

                databases.deleteDocument(
                    databaseId = databaseId,
                    collectionId = patientsCollectionId,
                    documentId = patientId
                )

                // Remove from local list
                val currentPatients = _patients.value.toMutableList()
                currentPatients.removeAll { it.id == patientId }
                _patients.value = currentPatients

                Logger.d("Patient deleted successfully: $patientId")
                true
            }
        } catch (e: AppwriteException) {
            Logger.e("Appwrite error deleting patient: ${e.message}")
            _error.value = e
            false
        } catch (e: Exception) {
            Logger.e("Unexpected error deleting patient: ${e.message}")
            _error.value = e
            false
        }
    }

    // Enhanced document parsing with better error handling
    private fun documentToPatient(document: Document<Map<String, Any>>): Patient {
        return try {
            val data = document.data
            Patient(
                id = document.id,
                name = data["name"] as? String ?: throw IllegalArgumentException("Missing name"),
                age = (data["age"] as? Number)?.toInt() ?: 0,
                gender = data["gender"] as? String ?: "",
                medicalHistory = data["medicalHistory"] as? String ?: "",
                lastRecorded = (data["lastRecorded"] as? Number)?.toLong() ?: 0L
            )
        } catch (e: Exception) {
            Logger.e("Error parsing patient document: ${e.message}")
            throw e
        }
    }

    suspend fun saveEcgRecord(
        patientId: String,
        ecgData: List<Int>,
        heartRate: Float,
        recordingDuration: Long,
        isAbnormal: Boolean = false
    ): String? {
        return try {
            withContext(Dispatchers.IO) {
                Logger.d("Saving ECG record for patient: $patientId")
                Logger.d("ECG data size: ${ecgData.size}, Heart rate: $heartRate, Duration: $recordingDuration, Abnormal: $isAbnormal")

                val recordData = mapOf(
                    "patientId" to patientId,
                    "ecgData" to ecgData,
                    "heartRate" to heartRate,
                    "recordingDuration" to recordingDuration,
                    "timestamp" to System.currentTimeMillis(),
                    "isAbnormal" to isAbnormal,
                    "sampleRate" to (ecgData.size.toFloat() / (recordingDuration / 1000.0f)),
                    "dataQuality" to analyzeDataQuality(ecgData)
                )

                Logger.d("Creating document in Appwrite...")
                val document = databases.createDocument(
                    databaseId = databaseId,
                    collectionId = ecgRecordsCollectionId,
                    documentId = ID.unique(),
                    data = recordData
                )

                Logger.d("Document created successfully with ID: ${document.id}")

                // Update patient's last recorded time
                updatePatientLastRecorded(patientId)

                document.id
            }
        } catch (e: AppwriteException) {
            Logger.e("Appwrite error saving ECG record: ${e.message}")
            Logger.e("Appwrite error code: ${e.code}")
            Logger.e("Appwrite error type: ${e.type}")
            _error.value = e
            null
        } catch (e: Exception) {
            Logger.e("Unexpected error saving ECG record: ${e.message}")
            e.printStackTrace()
            _error.value = e
            null
        }
    }

    private fun analyzeDataQuality(ecgData: List<Int>): String {
        return try {
            val mean = ecgData.average()
            val variance = ecgData.map { (it - mean) * (it - mean) }.average()
            val stdDev = kotlin.math.sqrt(variance)

            when {
                stdDev < 5 -> "Poor" // Very low variance, likely flat signal
                stdDev > 200 -> "Poor" // Very high variance, likely noisy
                ecgData.any { kotlin.math.abs(it) > 1000 } -> "Poor" // Extreme values
                else -> "Good"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    suspend fun deleteEcgRecord(recordId: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                Logger.d("Deleting ECG record: $recordId")

                databases.deleteDocument(
                    databaseId = databaseId,
                    collectionId = ecgRecordsCollectionId,
                    documentId = recordId
                )

                Logger.d("ECG record deleted successfully: $recordId")
                true
            }
        } catch (e: AppwriteException) {
            Logger.e("Appwrite error deleting ECG record: ${e.message}")
            _error.value = e
            false
        } catch (e: Exception) {
            Logger.e("Unexpected error deleting ECG record: ${e.message}")
            _error.value = e
            false
        }
    }

    // Enhanced getEcgRecords method with better error handling
    suspend fun getEcgRecords(patientId: String): List<EcgRecord> {
        return try {
            withContext(Dispatchers.IO) {
                Logger.d("Fetching ECG records for patient: $patientId")

                val response = databases.listDocuments(
                    databaseId = databaseId,
                    collectionId = ecgRecordsCollectionId,
                    queries = listOf(
                        io.appwrite.Query.equal("patientId", patientId),
                        io.appwrite.Query.orderDesc("timestamp"),
                        io.appwrite.Query.limit(100) // Increased limit
                    )
                )

                val records = response.documents.mapNotNull { document ->
                    try {
                        documentToEcgRecord(document)
                    } catch (e: Exception) {
                        Logger.e("Failed to parse ECG record: ${e.message}")
                        null
                    }
                }

                Logger.d("Successfully fetched ${records.size} ECG records")
                records
            }
        } catch (e: AppwriteException) {
            Logger.e("Failed to fetch ECG records: ${e.message}")
            _error.value = e
            emptyList()
        } catch (e: Exception) {
            Logger.e("Unexpected error fetching ECG records: ${e.message}")
            _error.value = e
            emptyList()
        }
    }

    // Enhanced documentToEcgRecord with better error handling
    private fun documentToEcgRecord(document: Document<Map<String, Any>>): EcgRecord {
        return try {
            val data = document.data
            EcgRecord(
                id = document.id,
                patientId = data["patientId"] as? String ?: "",
                ecgData = (data["ecgData"] as? List<*>)?.mapNotNull {
                    when (it) {
                        is Number -> it.toInt()
                        is String -> it.toIntOrNull()
                        else -> null
                    }
                } ?: emptyList(),
                heartRate = (data["heartRate"] as? Number)?.toFloat() ?: 0f,
                recordingDuration = (data["recordingDuration"] as? Number)?.toLong() ?: 0L,
                timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
                isAbnormal = data["isAbnormal"] as? Boolean ?: false
            )
        } catch (e: Exception) {
            Logger.e("Error parsing ECG record document: ${e.message}")
            throw e
        }
    }

    private suspend fun updatePatientLastRecorded(patientId: String) {
        try {
            databases.updateDocument(
                databaseId = databaseId,
                collectionId = patientsCollectionId,
                documentId = patientId,
                data = mapOf("lastRecorded" to System.currentTimeMillis())
            )
        } catch (e: AppwriteException) {
            Logger.e("Failed to update patient last recorded time: ${e.message}")
        }
    }


    fun clearError() {
        _error.value = null
    }
}

// Data class for ECG records
data class EcgRecord(
    val id: String,
    val patientId: String,
    val ecgData: List<Int>,
    val heartRate: Float,
    val recordingDuration: Long,
    val timestamp: Long,
    val isAbnormal: Boolean
)