package dev.atick.compose.ui.dashboard

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.rememberScaffoldState
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import dev.atick.compose.ui.common.components.TopBar
import dev.atick.compose.ui.dashboard.components.AbnormalEcgHeaderCard
import dev.atick.compose.ui.dashboard.components.DoctorInfoCard
import dev.atick.compose.ui.dashboard.components.EcgCard
import dev.atick.compose.ui.dashboard.components.EcgRecordsCard
import dev.atick.compose.ui.dashboard.components.HeartRateCard
import dev.atick.compose.ui.dashboard.components.PatientAddDialog
import dev.atick.compose.ui.dashboard.components.PatientEditDialog
import dev.atick.compose.ui.dashboard.data.Patient
import dev.atick.compose.ui.dashboard.data.RecordingState
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
@Composable
fun DashboardScreen(
    onExitClick: () -> Unit,
    onLogoutClick: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scanner = remember { BarcodeScanning.getClient() }

    var showBluetoothDialog by remember { mutableStateOf(false) }
    var showAddPatientDialog by remember { mutableStateOf(false) }
    var showEditPatientDialog by remember { mutableStateOf(false) }
    var patientToEdit by remember { mutableStateOf<Patient?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            val image = InputImage.fromBitmap(it, 0)
            scanner.process(image).addOnSuccessListener { qrCodes ->
                val qrCode = qrCodes.firstOrNull()
                qrCode?.let { code ->
                    code.rawValue?.let { doctor ->
                        val (name, id) = doctor.split(",")
                        Toast.makeText(
                            context,
                            "Connecting to Dr. $name",
                            Toast.LENGTH_LONG
                        ).show()
                        viewModel.connectDoctor(id)
                    } ?: Toast.makeText(
                        context,
                        "QR Scan Failed!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Bluetooth Device Selection Dialog
    if (showBluetoothDialog) {
        AlertDialog(
            onDismissRequest = { showBluetoothDialog = false },
            title = { Text("Select ECG Device") },
            text = {
                Column {
                    if (uiState.availableBluetoothDevices.isEmpty()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.padding(8.dp))
                            Text("Scanning for devices...")
                        }
                    } else {
                        LazyColumn {
                            items(uiState.availableBluetoothDevices) { device ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            viewModel.connectToDevice(device)
                                            showBluetoothDialog = false
                                        },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bluetooth,
                                            contentDescription = "Bluetooth Device"
                                        )
                                        Spacer(modifier = Modifier.padding(8.dp))
                                        Column {
                                            Text(
                                                text = device.name ?: "Unknown Device",
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = device.address,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                            )
                                            if (device.rssi != -100) {
                                                Text(
                                                    text = "Signal: ${device.rssi} dBm",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBluetoothDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Patient Dialog
    if (showAddPatientDialog) {
        PatientAddDialog(
            onDismiss = { showAddPatientDialog = false },
            onAddPatient = { patient ->
                viewModel.addPatient(patient)
                showAddPatientDialog = false
            }
        )
    }

    // Edit Patient Dialog
    patientToEdit?.let { patient ->
        PatientEditDialog(
            patient = patient,
            onDismiss = {
                patientToEdit = null
                showEditPatientDialog = false
            },
            onSavePatient = { updatedPatient ->
                viewModel.updatePatient(updatedPatient)
                patientToEdit = null
                showEditPatientDialog = false
            }
        )
    }

    return Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        scaffoldState = rememberScaffoldState(),
        topBar = {
            TopBar(
                title = "Doctor Dashboard",
                onScanClick = { launcher.launch(null) },
                onExitClick = onExitClick,
                onLogoutClick = {
                    coroutineScope.launch {
                        viewModel.logout()
                        onLogoutClick.invoke()
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.padding(16.dp),
                onClick = { showAddPatientDialog = true },
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Patient"
                    )
                    Text(text = "Add Patient",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colors.onPrimary
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))

                // Doctor Info Card
                DoctorInfoCard(
                    Modifier.fillMaxWidth(),
                    doctorName = uiState.doctorName,
                    doctorEmail = uiState.doctorEmail
                )
            }

            item {
                // Bluetooth connection status and device selection
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = MaterialTheme.colors.surface,
                    elevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "ECG Device",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colors.onSurface
                                )
                                Text(
                                    text = if (uiState.isConnectedToDevice)
                                        "Connected and ready"
                                    else
                                        "Not connected",
                                    fontSize = 14.sp,
                                    color = if (uiState.isConnectedToDevice)
                                        Color.Green
                                    else
                                        MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (uiState.isConnectedToDevice)
                                            Color.Green.copy(alpha = 0.1f)
                                        else
                                            MaterialTheme.colors.onSurface.copy(alpha = 0.1f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (uiState.isConnectedToDevice)
                                        Icons.Default.BluetoothConnected
                                    else
                                        Icons.Default.BluetoothDisabled,
                                    contentDescription = "Bluetooth Status",
                                    tint = if (uiState.isConnectedToDevice)
                                        Color.Green
                                    else
                                        MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Enhanced Connect/Disconnect Button
                        Button(
                            onClick = {
                                if (!uiState.isConnectedToDevice) {
                                    showBluetoothDialog = true
                                    viewModel.scanForDevices()
                                } else {
                                    viewModel.disconnectDevice()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isConnectedToDevice)
                                    MaterialTheme.colors.error
                                else
                                    MaterialTheme.colors.primary,
                                contentColor = if (uiState.isConnectedToDevice)
                                    MaterialTheme.colors.onError
                                else
                                    MaterialTheme.colors.onPrimary
                            ),
                            elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                                defaultElevation = 2.dp,
                                pressedElevation = 8.dp
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.isConnectedToDevice)
                                        Icons.Default.BluetoothDisabled
                                    else
                                        Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (uiState.isConnectedToDevice)
                                        "Disconnect Device"
                                    else
                                        "Connect to ECG Device",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (uiState.isConnectedToDevice)
                                        MaterialTheme.colors.onError
                                    else
                                        MaterialTheme.colors.onPrimary
                                )
                            }
                        }

                        // Connection Status Indicator (optional additional info)
                        if (uiState.isConnectedToDevice) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Color.Green.copy(alpha = 0.1f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.Green)
                                )
                                Text(
                                    text = "Device connected and transmitting data",
                                    fontSize = 12.sp,
                                    color = Color.Green.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Selected Patient Card (when a patient is selected)
            uiState.selectedPatient?.let { patient ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = MaterialTheme.colors.primary
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Selected Patient",
                                fontSize = 16.sp,
                                color = MaterialTheme.colors.onPrimary
                            )
                            Text(
                                text = patient.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.onPrimary
                            )
                            Text(
                                text = "${patient.age} years old • ${patient.gender}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colors.onPrimary.copy(alpha = 0.8f)
                            )
                            if (patient.medicalHistory.isNotEmpty()) {
                                Text(
                                    text = "Medical History: ${patient.medicalHistory}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colors.onPrimary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                item {
                    EcgRecordsCard(
                        modifier = Modifier.fillMaxWidth(),
                        ecgRecords = uiState.patientEcgRecords,
                        isLoading = uiState.isLoadingRecords,
                        onDeleteRecord = { recordId ->
                            viewModel.deleteEcgRecord(recordId)
                        },
                        onViewRecord = { record ->
                            // Convert EcgRecord to EcgPlotData and show in a dialog or new screen
                            viewModel.viewEcgRecord(record)
                        }
                    )
                }
            }

            // Heart Rate Card
            item {
                HeartRateCard(
                    modifier = Modifier.fillMaxWidth(),
                    heartRate = uiState.heartRate
                )
            }

            // ECG Monitoring Card
            item {
                EcgCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Live ECG",
                    ecgPlotData = uiState.ecgPlotData,
                    recordingState = uiState.recordingState,
                    onRecordClick = {
                        when (uiState.recordingState) {
                            RecordingState.NotRecording -> {
                                if (uiState.selectedPatient != null) {
                                    viewModel.startRecording()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Please select a patient first",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            RecordingState.Recording -> viewModel.stopRecording()
                            RecordingState.PatientRecording -> viewModel.stopRecording()
                            RecordingState.ReadyToRecord -> viewModel.startRecording()
                        }
                    }
                )
            }

            // Abnormal ECG Header Card
            if (uiState.abnormalEcgPlotData.isNotEmpty()) {
                item {
                    AbnormalEcgHeaderCard(modifier = Modifier.fillMaxWidth())
                }

                // Display abnormal ECG data
                items(uiState.abnormalEcgPlotData) { abnormalEcg ->
                    EcgCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = "Abnormal ECG - ${abnormalEcg.getTimestamp()}",
                        ecgPlotData = abnormalEcg
                    )
                }
            }

// Patient List
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = MaterialTheme.colors.surface
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Patients",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium
                            )

                            // Add refresh button
                            IconButton(
                                onClick = {
                                    viewModel.refreshPatients()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Patients",
                                    tint = MaterialTheme.colors.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        when {
                            uiState.patients.isEmpty() -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "No Patients",
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "No patients added yet",
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Tap the + button to add your first patient",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            else -> {
                                uiState.patients.forEach { patient ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable { viewModel.selectPatient(patient) }
                                            .let { modifier ->
                                                if (uiState.selectedPatient?.id == patient.id) {
                                                    modifier.border(
                                                        2.dp,
                                                        MaterialTheme.colors.primary,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                } else {
                                                    modifier
                                                }
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        backgroundColor = if (uiState.selectedPatient?.id == patient.id)
                                            MaterialTheme.colors.primary.copy(alpha = 0.1f)
                                        else
                                            MaterialTheme.colors.background
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colors.primary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = "Patient",
                                                    tint = MaterialTheme.colors.onPrimary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.padding(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = patient.name,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "${patient.age} years old • ${patient.gender}",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                                )
                                                if (patient.medicalHistory.isNotEmpty()) {
                                                    Text(
                                                        text = patient.medicalHistory,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            // Add menu for patient actions
                                            var showMenu by remember { mutableStateOf(false) }

                                            Box {
                                                IconButton(
                                                    onClick = { showMenu = true }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.MoreVert,
                                                        contentDescription = "Patient Options"
                                                    )
                                                }

                                                DropdownMenu(
                                                    expanded = showMenu,
                                                    onDismissRequest = { showMenu = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        onClick = {
                                                            showMenu = false
                                                            patientToEdit = patient
                                                            showEditPatientDialog = true
                                                        }
                                                    ) {
                                                        Text("Edit",
                                                            color = MaterialTheme.colors.primary)

                                                    }
                                                    DropdownMenuItem(
                                                        onClick = {
                                                            showMenu = false
                                                            viewModel.deletePatient(patient)
                                                        }
                                                    ) {
                                                        Text(
                                                            text = "Delete",
                                                            color = androidx.compose.material3.MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

