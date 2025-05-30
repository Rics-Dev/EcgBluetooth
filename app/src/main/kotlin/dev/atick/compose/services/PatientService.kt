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
        recordingDuration: Long
    ): String? {
        return try {
            withContext(Dispatchers.IO) {
                val recordData = mapOf(
                    "patientId" to patientId,
                    "ecgData" to ecgData,
                    "heartRate" to heartRate,
                    "recordingDuration" to recordingDuration,
                    "timestamp" to System.currentTimeMillis(),
                    "isAbnormal" to false // This would be determined by ML analysis
                )

                val document = databases.createDocument(
                    databaseId = databaseId,
                    collectionId = ecgRecordsCollectionId,
                    documentId = ID.unique(),
                    data = recordData
                )

                // Update patient's last recorded time
                updatePatientLastRecorded(patientId)

                document.id
            }
        } catch (e: AppwriteException) {
            Logger.e("Failed to save ECG record: ${e.message}")
            _error.value = e
            null
        }
    }

    suspend fun getEcgRecords(patientId: String): List<EcgRecord> {
        return try {
            withContext(Dispatchers.IO) {
                val response = databases.listDocuments(
                    databaseId = databaseId,
                    collectionId = ecgRecordsCollectionId,
                    queries = listOf(
                        io.appwrite.Query.equal("patientId", patientId),
                        io.appwrite.Query.orderDesc("timestamp"),
                        io.appwrite.Query.limit(50) // Limit to recent 50 records
                    )
                )

                response.documents.mapNotNull { document ->
                    try {
                        documentToEcgRecord(document)
                    } catch (e: Exception) {
                        Logger.e("Failed to parse ECG record: ${e.message}")
                        null
                    }
                }
            }
        } catch (e: AppwriteException) {
            Logger.e("Failed to fetch ECG records: ${e.message}")
            _error.value = e
            emptyList()
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


    private fun documentToEcgRecord(document: Document<Map<String, Any>>): EcgRecord {
        val data = document.data
        return EcgRecord(
            id = document.id,
            patientId = data["patientId"] as? String ?: "",
            ecgData = (data["ecgData"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList(),
            heartRate = (data["heartRate"] as? Number)?.toFloat() ?: 0f,
            recordingDuration = (data["recordingDuration"] as? Number)?.toLong() ?: 0L,
            timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
            isAbnormal = data["isAbnormal"] as? Boolean ?: false
        )
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