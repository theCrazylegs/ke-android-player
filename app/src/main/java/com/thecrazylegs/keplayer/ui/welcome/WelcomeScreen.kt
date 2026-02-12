package com.thecrazylegs.keplayer.ui.welcome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.thecrazylegs.keplayer.R
import kotlinx.coroutines.delay

private val AccentPink = Color(0xFFFD80D8)

@Composable
fun WelcomeScreen(
    viewModel: WelcomeViewModel,
    onPaired: () -> Unit,
    onManualLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var isMenuOpen by remember { mutableStateOf(false) }
    val rootFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }

    LaunchedEffect(uiState.isPaired) {
        if (uiState.isPaired) {
            onPaired()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            if (!isMenuOpen) {
                                isMenuOpen = true
                                true
                            } else false
                        }
                        Key.Back -> {
                            if (isMenuOpen) {
                                isMenuOpen = false
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Background image (waiting_screen.png)
        Image(
            painter = painterResource(id = R.drawable.waiting_screen),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Bottom banner with pairing status
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 30.dp, vertical = 20.dp)
        ) {
            when (uiState.discoveryStatus) {
                "discovering" -> DiscoveringBanner()
                "not_found" -> NotFoundBanner()
                "found_no_ip" -> FoundNoIpBanner()
                "requesting_code" -> RequestingCodeBanner(serverUrl = uiState.serverUrl)
                "showing_code" -> ShowingCodeBanner(
                    code = uiState.code,
                    serverUrl = uiState.serverUrl
                )
                "confirmed" -> ConfirmedBanner()
                "error" -> ErrorBanner(error = uiState.error)
            }
        }

        // Side menu overlay
        WelcomeSideMenu(
            isVisible = isMenuOpen,
            serverUrl = uiState.serverUrl,
            onServerUrlChange = { viewModel.setServerUrl(it) },
            onConnectWithUrl = { url ->
                isMenuOpen = false
                viewModel.setServerUrl(url)
                viewModel.requestCode()
            },
            onManualLogin = {
                isMenuOpen = false
                onManualLogin()
            },
            onRetryDiscovery = {
                isMenuOpen = false
                viewModel.retryDiscovery()
            },
            onDismiss = { isMenuOpen = false }
        )
    }
}

// --- Banner composables ---

@Composable
private fun DiscoveringBanner() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        SpinnerIcon()
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Searching for Karaoke Eternal server...",
            color = Color.White,
            fontSize = 22.sp
        )
    }
}

@Composable
private fun NotFoundBanner() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Server not found",
            color = Color(0xFFFF9800),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Press \u2190 for manual setup",
            color = Color(0xFFAAAAAA),
            fontSize = 16.sp
        )
    }
}

@Composable
private fun FoundNoIpBanner() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Server found on network!",
            color = Color(0xFF4CAF50),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Press \u2190 and enter server URL to connect",
            color = Color(0xFFAAAAAA),
            fontSize = 16.sp
        )
    }
}

@Composable
private fun RequestingCodeBanner(serverUrl: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        SpinnerIcon()
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Connecting to $serverUrl...",
            color = Color.White,
            fontSize = 20.sp
        )
    }
}

@Composable
private fun ShowingCodeBanner(code: String, serverUrl: String) {
    val fullUrl = "http://$serverUrl"
    val pairUrl = "http://$serverUrl/api/pair/link/$code"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: QR code (auto-pairing URL) + instructions
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            QrCodeImage(content = pairUrl, size = 90.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Scan to pair automatically",
                    color = Color(0xFFAAAAAA),
                    fontSize = 14.sp
                )
                Text(
                    text = fullUrl,
                    color = Color(0xFFFD80D8),
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Or enter code:",
                    color = Color(0xFFAAAAAA),
                    fontSize = 14.sp
                )
            }
        }

        // Center: code display
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (char in code) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .border(
                            width = 2.dp,
                            color = Color(0xFFFD80D8),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(
                            Color.Black.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = char.toString(),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Right: waiting indicator
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpinnerIcon()
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "waiting",
                color = Color(0xFFAAAAAA),
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun ConfirmedBanner() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Paired! Connecting...",
            color = Color(0xFF4CAF50),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ErrorBanner(error: String?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = error ?: "Connection error",
            color = MaterialTheme.colorScheme.error,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Press \u2190 for manual setup",
            color = Color(0xFFAAAAAA),
            fontSize = 16.sp
        )
    }
}

@Composable
private fun QrCodeImage(content: String, size: Dp = 100.dp) {
    val bitmap = remember(content) {
        try {
            val writer = QRCodeWriter()
            val matrix: BitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 256, 256)
            val width = matrix.width
            val height = matrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(
                        x, y,
                        if (matrix.get(x, y)) android.graphics.Color.WHITE
                        else android.graphics.Color.TRANSPARENT
                    )
                }
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Box(
            modifier = Modifier
                .size(size)
                .border(2.dp, Color(0xFFFD80D8), RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "QR Code",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
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
        text = "\u21BB",
        fontSize = 24.sp,
        modifier = Modifier.rotate(rotation),
        color = Color(0xFFFD80D8)
    )
}

// --- Side menu for Welcome screen (disconnected mode) ---

@Composable
private fun WelcomeSideMenu(
    isVisible: Boolean,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    onConnectWithUrl: (String) -> Unit,
    onManualLogin: () -> Unit,
    onRetryDiscovery: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(150)
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(250))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
        }

        // Sliding panel
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(300)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(250)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .background(Color(0xE6121212))
                    .padding(vertical = 24.dp)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionRight, Key.Back -> {
                                    onDismiss()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            ) {
                // Header
                Text(
                    text = "KE Player",
                    color = AccentPink,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Text(
                    text = "Not connected",
                    color = Color(0xFFFF9800),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                WelcomeMenuDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Server URL section
                Text(
                    text = "SERVER",
                    color = Color(0xFF777777),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )

                // Editable server URL
                var editableUrl by remember(serverUrl) { mutableStateOf(serverUrl) }
                OutlinedTextField(
                    value = editableUrl,
                    onValueChange = { editableUrl = it },
                    placeholder = {
                        Text(
                            "192.168.1.100:3000",
                            color = Color(0xFF555555),
                            fontSize = 14.sp
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    textStyle = LocalTextStyle.current.copy(
                        color = Color.White,
                        fontSize = 14.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFD80D8),
                        unfocusedBorderColor = Color(0xFF444444),
                        cursorColor = Color(0xFFFD80D8)
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onConnectWithUrl(editableUrl)
                        }
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Connect with code button
                WelcomeMenuItem(
                    icon = Icons.Default.Link,
                    label = "Connect with code",
                    onClick = {
                        onConnectWithUrl(editableUrl)
                    },
                    modifier = Modifier.focusRequester(focusRequester),
                    contentColor = AccentPink
                )

                Spacer(modifier = Modifier.height(4.dp))
                WelcomeMenuDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Manual login
                WelcomeMenuItem(
                    icon = Icons.AutoMirrored.Filled.Login,
                    label = "Manual Login",
                    onClick = onManualLogin
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Retry discovery
                WelcomeMenuItem(
                    icon = Icons.Default.Refresh,
                    label = "Retry Discovery",
                    onClick = onRetryDiscovery
                )

                Spacer(modifier = Modifier.height(4.dp))
                WelcomeMenuDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Settings placeholder
                Text(
                    text = "SETTINGS",
                    color = Color(0xFF777777),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color(0xFF444444),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Coming soon",
                        color = Color(0xFF555555),
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Version
                Text(
                    text = "v1.0",
                    color = Color(0xFF444444),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun WelcomeMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter)
                ) {
                    onClick()
                    true
                } else false
            }
            .background(
                if (isFocused) Color(0xFF2A2A2A) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .then(
                if (isFocused) Modifier.border(
                    width = 2.dp,
                    color = AccentPink,
                    shape = RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isFocused) Color.Yellow else AccentPink.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = if (isFocused) Color.Yellow else contentColor,
            fontSize = 18.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun WelcomeMenuDivider() {
    HorizontalDivider(
        color = Color(0xFF333333),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}
