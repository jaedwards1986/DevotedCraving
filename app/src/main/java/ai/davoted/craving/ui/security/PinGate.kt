package ai.davoted.craving.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ai.davoted.craving.security.PinCredentialStore

@Composable
fun PinGate(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val hasPin = remember { PinCredentialStore.isConfigured(context) }
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val confirming = !hasPin && firstPin.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "UNBOUNDAI",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = when {
                hasPin -> "Enter your six-digit PIN"
                confirming -> "Confirm your six-digit PIN"
                else -> "Create your six-digit PIN"
            },
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (hasPin) "Your private workspace is locked." else "Set a PIN to protect access to the app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = if (confirming) confirmation else pin,
            onValueChange = { value ->
                if (value.length <= 6 && value.all { it in '0'..'9' }) {
                    if (confirming) confirmation = value else pin = value
                    message = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(if (confirming) "Confirm PIN" else "Six-digit PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                message = when {
                    hasPin -> {
                        if (PinCredentialStore.verifyPin(context, pin)) {
                            pin = ""
                            onUnlocked()
                            ""
                        } else {
                            pin = ""
                            "Incorrect PIN."
                        }
                    }
                    !confirming -> {
                        if (pin.length == 6) {
                            firstPin = pin
                            pin = ""
                            ""
                        } else "Enter all six digits."
                    }
                    confirmation.length != 6 -> "Enter all six confirmation digits."
                    confirmation != firstPin -> {
                        confirmation = ""
                        "The PINs do not match. Try again."
                    }
                    !PinCredentialStore.createPin(context, firstPin) -> {
                        confirmation = ""
                        "PIN setup failed. Try again."
                    }
                    else -> {
                        pin = ""
                        confirmation = ""
                        firstPin = ""
                        onUnlocked()
                        ""
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = if (hasPin) pin.length == 6 else if (confirming) confirmation.length == 6 else pin.length == 6
        ) {
            Text(
                when {
                    hasPin -> "Unlock"
                    confirming -> "Confirm and protect"
                    else -> "Continue"
                }
            )
        }
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}
