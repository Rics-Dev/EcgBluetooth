package dev.atick.compose.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.atick.compose.R
import dev.atick.compose.ui.common.components.InputField

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current

    var email by viewModel.email.state
    var password by viewModel.password.state
    var confirmPassword by viewModel.confirmPassword.state
    var name by viewModel.name.state

    val isLoginMode by viewModel.isLoginMode
    val isAuthenticating by viewModel.isAuthenticating

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                modifier = Modifier.width(100.dp),
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = "App Logo"
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isLoginMode) "Login to your Account" else "Create Doctor Account",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Name field (only for signup)
            if (!isLoginMode) {
                InputField(
                    modifier = Modifier.fillMaxWidth(0.8F),
                    value = name,
                    onValueChange = { name = it },
                    labelResourceId = R.string.name,
                    leadingIcon = Icons.Default.Person
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Email field
            InputField(
                modifier = Modifier.fillMaxWidth(0.8F),
                value = email,
                onValueChange = { email = it },
                labelResourceId = R.string.email,
                leadingIcon = Icons.Default.Email
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Password field
            InputField(
                modifier = Modifier.fillMaxWidth(0.8F),
                value = password,
                onValueChange = { password = it },
                labelResourceId = R.string.password,
                leadingIcon = Icons.Default.Password,
                isPasswordField = true
            )

            // Confirm password field (only for signup)
            if (!isLoginMode) {
                Spacer(modifier = Modifier.height(8.dp))

                InputField(
                    modifier = Modifier.fillMaxWidth(0.8F),
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    labelResourceId = R.string.confirm_password,
                    leadingIcon = Icons.Default.Password,
                    isPasswordField = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                modifier = Modifier
                    .fillMaxWidth(0.8F)
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                onClick = { viewModel.authenticate() },
                enabled = !isAuthenticating
            ) {
                if (isAuthenticating) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colors.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(text = if (isLoginMode) "Login" else "Sign Up")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Toggle between login and signup mode
            TextButton(onClick = { viewModel.toggleAuthMode() }) {
                Text(
                    text = if (isLoginMode)
                        "Don't have an account? Sign Up"
                    else
                        "Already have an account? Login",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.primary
                )
            }
        }
    }
}
