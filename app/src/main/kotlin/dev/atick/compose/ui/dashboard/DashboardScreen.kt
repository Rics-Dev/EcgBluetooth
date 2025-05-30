package dev.atick.compose.ui.dashboard

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.rememberScaffoldState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.orhanobut.logger.Logger
import dev.atick.compose.ui.common.components.TopBar
import dev.atick.compose.ui.dashboard.components.AbnormalEcgHeaderCard
import dev.atick.compose.ui.dashboard.components.AudioRecordDialog
import dev.atick.compose.ui.dashboard.components.DoctorInfoCard
import dev.atick.compose.ui.dashboard.components.EcgCard
import dev.atick.compose.ui.dashboard.components.HeartRateCard
import dev.atick.compose.ui.dashboard.components.PatientAddDialog
import dev.atick.compose.ui.dashboard.data.BluetoothDevice
import dev.atick.compose.ui.dashboard.data.Patient
import dev.atick.compose.ui.dashboard.data.RecordingState
import kotlinx.coroutines.launch

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

    var showRecordingDialog by remember { mutableStateOf(false) }
    var showBluetoothDialog by remember { mutableStateOf(false) }
    var showAddPatientDialog by remember { mutableStateOf(false) }

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
                modifier = Modifier.size(64.dp),
                onClick = { showAddPatientDialog = true }
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Patient")
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(it)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Doctor Info Card
                    DoctorInfoCard(
                        Modifier.fillMaxWidth(),
                        doctorName = uiState.doctorName,
                        doctorEmail = uiState.doctorEmail
                    )

                    // Bluetooth connection status and device selection
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = MaterialTheme.colors.surface
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "ECG Device",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Icon(
                                    imageVector = if (uiState.isConnectedToDevice)
                                        Icons.Default.BluetoothConnected
                                    else
                                        Icons.Default.BluetoothDisabled,
                                    contentDescription = "Bluetooth Status",
                                    tint = if (uiState.isConnectedToDevice)
                                        Color.Green
                                    else
                                        MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                )
                            }

                            Button(
                                onClick = {
                                    if (!uiState.isConnectedToDevice) {
                                        showBluetoothDialog = true
                                        viewModel.scanForDevices()
                                    } else {
                                        viewModel.disconnectDevice()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (uiState.isConnectedToDevice)
                                        "Disconnect"
                                    else
                                        "Connect to Device"
                                )
                            }
                        }
                    }

                    // Selected Patient Card (when a patient is selected)
                    uiState.selectedPatient?.let { patient ->
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
                                    text = "${patient.age


