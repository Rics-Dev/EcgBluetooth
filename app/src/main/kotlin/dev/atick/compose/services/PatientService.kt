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
    private val databaseId = "cardiac_zone_db"
    private val patientsCollectionId = "patients"
    private val ecgRecordsCollectionId = "ecg_records"

    private val _patients = MutableStateFlow<List<Patient>>(emptyList())
    val patients: StateFlow<List<Patient>> = _patients.asStateFlow()

    private val _error = MutableStateFlow<Exception?>(null)
    val error: StateFlow<Exception?> = _error.asStateFlow()

    suspend fun createPatient(patient: Patient, doctorId: String): Patient? {
        return try {
            withContext(Dispatchers.IO) {
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

                documentToPatient(document)
            }
        } catch (e: AppwriteException) {
            Logger.e("Failed to create patient: ${e.message}")
            _error.value = e
            null
        }
    }

    suspend fun getPatientsByDoctor(doctorId: String): List<Patient> {
        return try {
            withContext(Dispatchers.IO) {
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
                        Logger.e("Failed to parse patient document: ${e.message}")
                        null
                    }
                }

                _patients.value = patients
                patients
            }
        } catch (e: AppwriteException) {
            Logger.e("Failed to fetch patients: ${e.message}")
            _error.value = e
            emptyList()
        }
    }

    suspend fun updatePatient(patient: Patient): Patient? {
        return try {
            withContext(Dispatchers.IO) {
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

                documentToPatient(document)
            }
        } catch (e: AppwriteException) {
            Logger.e("Failed to update patient: ${e.message}")
            _error.value = e
            null
        }
    }

    suspend fun deletePatient(patientId: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                databases.deleteDocument(
                    databaseId = databaseId,
                    collectionId = patientsCollectionId,
                    documentId = patientId
                )

                // Remove from local list
                _patients.value = _patients.value.filter { it.id != patientId }
                true
            }
        } catch (e: AppwriteException) {
            Logger.e("Failed to delete patient: ${e.message}")
            _error.value = e
            false
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

    private fun documentToPatient(document: Document<Map<String, Any>>): Patient {
        val data = document.data
        return Patient(
            id = document.id,
            name = data["name"] as? String ?: "",
            age = (data["age"] as? Number)?.toInt() ?: 0,
            gender = data["gender"] as? String ?: "",
            medicalHistory = data["medicalHistory"] as? String ?: "",
            lastRecorded = (data["lastRecorded"] as? Number)?.toLong() ?: 0L
        )
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