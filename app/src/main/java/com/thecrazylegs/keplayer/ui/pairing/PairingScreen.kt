package com.thecrazylegs.keplayer.ui.pairing

import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PairingScreen(
    viewModel: PairingViewModel,
    onPairingSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isPaired) {
        if (uiState.isPaired) {
            onPairingSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Device Pairing",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Enter this code in the web app",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Code display
                when (uiState.status) {
                    "loading" -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    "error" -> {
                        Text(
                            text = uiState.error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        var isRetryFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = { viewModel.requestCode() },
                            modifier = Modifier
                                .onFocusChanged { isRetryFocused = it.isFocused }
                                .focusable()
                                .then(
                                    if (isRetryFocused) Modifier.border(3.dp, Color.Yellow, RoundedCornerShape(4.dp))
                                    else Modifier
                                )
                        ) {
                            Text("Retry")
                        }
                    }
                    else -> {
                        // Code boxes (OTP style)
                        CodeDisplay(code = uiState.code)

                        Spacer(modifier = Modifier.height(8.dp))

                        // Status indicator
                        if (uiState.status == "pending") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                SpinnerIcon()
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Waiting for confirmation...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (uiState.status == "confirmed") {
                            Text(
                                text = "Paired! Connecting...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Server info
                Text(
                    text = "Server: ${uiState.serverUrl}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Cancel button
                var isCancelFocused by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = {
                        viewModel.cancelPolling()
                        onCancel()
                    },
                    modifier = Modifier
                        .onFocusChanged { isCancelFocused = it.isFocused }
                        .focusable()
                        .then(
                            if (isCancelFocused) Modifier.border(3.dp, Color.Yellow, RoundedCornerShape(4.dp))
                            else Modifier
                        )
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun CodeDisplay(code: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (char in code) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .border(
                        width = 2.dp,
                        color = Color(0xFFFD80D8),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = char.toString(),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun SpinnerIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    Text(
        text = "\u21BB", // ↻ refresh symbol
        fontSize = 20.sp,
        modifier = Modifier.rotate(rotation),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
