package dev.atick.compose.ui.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.atick.compose.ui.dashboard.data.Patient

@Composable
fun PatientEditDialog(
    patient: Patient,
    onDismiss: () -> Unit,
    onSavePatient: (Patient) -> Unit
) {
    var name by remember { mutableStateOf(patient.name) }
    var age by remember { mutableStateOf(patient.age.toString()) }
    var gender by remember { mutableStateOf(patient.gender) }
    var medicalHistory by remember { mutableStateOf(patient.medicalHistory) }

    val isFormValid = name.isNotBlank() && age.isNotBlank() && gender.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Edit Patient",
                    style = MaterialTheme.typography.h6
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = age,
                        onValueChange = { age = it },
                        label = { Text("Age") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        )
                    )

                    OutlinedTextField(
                        value = gender,
                        onValueChange = { gender = it },
                        label = { Text("Gender") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = medicalHistory,
                    onValueChange = { medicalHistory = it },
                    label = { Text("Medical History") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 3
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (isFormValid) {
                                val updatedPatient = patient.copy(
                                    name = name,
                                    age = age.toIntOrNull() ?: 0,
                                    gender = gender,
                                    medicalHistory = medicalHistory
                                )
                                onSavePatient(updatedPatient)
                            }
                        },
                        enabled = isFormValid
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}
