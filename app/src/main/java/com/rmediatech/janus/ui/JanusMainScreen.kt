package com.rmediatech.janus.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmediatech.janus.bridge.BridgeState
import com.rmediatech.janus.bridge.JanusBridgeService
import com.rmediatech.janus.clipboard.JanusClipboardBridge
import com.rmediatech.janus.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun JanusMainScreen(
    bridgeState: BridgeState,
    onToggleBridge: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var mobileClipInput by remember { mutableStateOf("") }
    val latestItem by JanusClipboardBridge.latestState.collectAsState()
    val latestSyncedText = latestItem?.text ?: ""
    var showAboutDialog by remember { mutableStateOf(false) }

    // Optical ACK flash animatables
    val serverCardBorderFlash = remember { Animatable(Color.Transparent) }
    val clipCardBorderFlash = remember { Animatable(Color.Transparent) }
    val urlBoxBorderFlash = remember { Animatable(Color.Transparent) }
    val pinBoxBorderFlash = remember { Animatable(Color.Transparent) }

    // Pulsing beacon for live connections
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Trigger optical flash on connection count change
    LaunchedEffect(bridgeState.connectedClients) {
        if (bridgeState.connectedClients > 0) {
            serverCardBorderFlash.animateTo(Color(0xFF10B981).copy(alpha = 0.9f), tween(80))
            serverCardBorderFlash.animateTo(Color.Transparent, tween(200))
        }
    }

    // Trigger optical flash on new clipboard sync
    LaunchedEffect(latestItem?.id) {
        if (latestItem != null) {
            val flashColor = if (latestItem?.source == "desktop") CyberCyan else CyberAmber
            clipCardBorderFlash.animateTo(flashColor.copy(alpha = 0.9f), tween(70))
            clipCardBorderFlash.animateTo(Color.Transparent, tween(180))
        }
    }

    val pairingUrl = remember(bridgeState) {
        if (bridgeState.isRunning) {
            "http://${bridgeState.localIp}:${bridgeState.port}/?token=${bridgeState.sessionSecret}"
        } else ""
    }

    val qrBitmap = remember(pairingUrl) {
        if (pairingUrl.isNotEmpty()) {
            QrCodeGenerator.generateQrBitmap(pairingUrl, 380)
        } else null
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = DarkSurface,
            titleContentColor = CyberCyan,
            textContentColor = TextMain,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("About Janus", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("Janus Sovereign Bridge v1.0.0", fontWeight = FontWeight.Bold, color = CyberCyan)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "An ultra-low latency, zero-cloud workstation bridge connecting mobile devices and desktop environments across local networks.",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DarkCard)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("DEVELOPER & ARCHITECTURE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Crafted by RMediaTech", fontWeight = FontWeight.Bold, color = TextMain)
                    Text("https://rmediatech.com", color = CyberCyan, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "100% Free • Zero Ads • Zero Cloud Relays • Pure Local Sovereign Engineering",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close", color = CyberCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkCyberBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        // 1. Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "JANUS",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = CyberCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CyberCyan.copy(alpha = 0.15f))
                            .border(1.dp, CyberCyan, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "SOVEREIGN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan
                        )
                    }
                }
                Text(
                    text = "Desktop • Mobile Bi-Directional Bridge",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            // Status Badge & About Button
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (badgeBg, badgeBorder, badgeTextColor, badgeLabel) = when {
                    !bridgeState.isRunning -> Quadruple(
                        Color.DarkGray.copy(alpha = 0.25f),
                        Color.Gray.copy(alpha = 0.4f),
                        Color.LightGray,
                        "OFFLINE"
                    )
                    bridgeState.connectedClients > 0 -> Quadruple(
                        Color(0xFF10B981).copy(alpha = 0.15f),
                        Color(0xFF10B981),
                        Color(0xFF10B981),
                        if (bridgeState.connectedClients == 1) "DESKTOP LINKED" else "${bridgeState.connectedClients} LINKED"
                    )
                    else -> Quadruple(
                        CyberCyan.copy(alpha = 0.12f),
                        CyberCyan.copy(alpha = 0.5f),
                        CyberCyan,
                        "STANDBY"
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(badgeBg)
                        .border(1.dp, badgeBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (bridgeState.isRunning && bridgeState.connectedClients > 0) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981).copy(alpha = pulseAlpha * 0.45f))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                            }
                        } else if (bridgeState.isRunning) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(CyberCyan)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.Gray)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = badgeLabel,
                            color = badgeTextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = { showAboutDialog = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "About Janus & RMediaTech",
                        tint = CyberCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Master Activation Card
        val serverBorderColor = when {
            serverCardBorderFlash.value != Color.Transparent -> serverCardBorderFlash.value
            bridgeState.isRunning && bridgeState.connectedClients > 0 -> Color(0xFF10B981).copy(alpha = 0.5f)
            bridgeState.isRunning -> CyberCyan.copy(alpha = 0.4f)
            else -> DarkCard
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, serverBorderColor)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Bridge Server",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMain
                        )
                        Text(
                            text = if (bridgeState.isRunning) {
                                if (bridgeState.connectedClients > 0) {
                                    "Listening on :${bridgeState.port} • ${bridgeState.connectedClients} active workstation${if (bridgeState.connectedClients > 1) "s" else ""}"
                                } else {
                                    "Listening on :${bridgeState.port} • Standby for pairing"
                                }
                            } else {
                                "Service stopped"
                            },
                            fontSize = 13.sp,
                            color = if (bridgeState.isRunning && bridgeState.connectedClients > 0) Color(0xFF10B981) else TextMuted
                        )
                    }

                    Switch(
                        checked = bridgeState.isRunning,
                        onCheckedChange = { onToggleBridge(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = CyberCyan
                        )
                    )
                }

                if (bridgeState.isRunning) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = DarkCard)

                    // URL row
                    Text(text = "BROWSER ACCESS URL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkCyberBg)
                            .border(
                                1.dp,
                                if (urlBoxBorderFlash.value != Color.Transparent) urlBoxBorderFlash.value else DarkCard,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "http://${bridgeState.localIp}:${bridgeState.port}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = CyberCyan
                        )
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Janus URL", "http://${bridgeState.localIp}:${bridgeState.port}"))
                                scope.launch {
                                    urlBoxBorderFlash.animateTo(CyberCyan.copy(alpha = 0.8f), tween(70))
                                    urlBoxBorderFlash.animateTo(Color.Transparent, tween(150))
                                }
                                Toast.makeText(context, "URL Copied!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", tint = CyberCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // PIN Code
                    Text(text = "DESKTOP PAIRING PIN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkCyberBg)
                            .border(
                                1.dp,
                                if (pinBoxBorderFlash.value != Color.Transparent) pinBoxBorderFlash.value else DarkCard,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Janus PIN", bridgeState.pairingPin))
                                scope.launch {
                                    pinBoxBorderFlash.animateTo(CyberAmber.copy(alpha = 0.8f), tween(70))
                                    pinBoxBorderFlash.animateTo(Color.Transparent, tween(150))
                                }
                                Toast.makeText(context, "PIN Copied!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = bridgeState.pairingPin,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp,
                            color = CyberAmber
                        )
                    }

                    // QR Code
                    if (qrBitmap != null) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "INSTANT CAMERA / WEBCAM PAIRING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 30.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBitmap,
                                contentDescription = "Janus Instant Pairing QR Code",
                                modifier = Modifier.size(200.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Quick Clipboard Bridge Card
        val clipBorderColor = when {
            clipCardBorderFlash.value != Color.Transparent -> clipCardBorderFlash.value
            latestItem != null -> CyberCyan.copy(alpha = 0.3f)
            else -> DarkCard
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, clipBorderColor)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Clipboard Bridge",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMain
                        )
                        if (bridgeState.isRunning && bridgeState.connectedClients > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "LIVE WS",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    }
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = null,
                        tint = if (bridgeState.isRunning && bridgeState.connectedClients > 0) Color(0xFF10B981) else CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Latest from desktop or phone
                if (latestSyncedText.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "LATEST SYNCED ITEM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                            Spacer(modifier = Modifier.width(8.dp))
                            val isDesktop = latestItem?.source == "desktop"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isDesktop) CyberCyan.copy(alpha = 0.15f) else CyberAmber.copy(alpha = 0.15f))
                                    .border(1.dp, if (isDesktop) CyberCyan.copy(alpha = 0.5f) else CyberAmber.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = if (isDesktop) "DESKTOP" else "PHONE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDesktop) CyberCyan else CyberAmber
                                )
                            }
                        }
                        Text(
                            text = "${latestSyncedText.length} chars",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkCyberBg)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = latestSyncedText,
                            fontSize = 13.sp,
                            color = TextMain,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Janus Sync", latestSyncedText))
                                scope.launch {
                                    clipCardBorderFlash.animateTo(CyberCyan.copy(alpha = 0.8f), tween(70))
                                    clipCardBorderFlash.animateTo(Color.Transparent, tween(150))
                                }
                                Toast.makeText(context, "Copied to Phone Clipboard!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = CyberCyan)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Send to desktop
                OutlinedTextField(
                    value = mobileClipInput,
                    onValueChange = { mobileClipInput = it },
                    placeholder = { Text("Push text from phone to desktop...", color = TextMuted, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = DarkCard,
                        focusedTextColor = TextMain,
                        unfocusedTextColor = TextMain
                    ),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (mobileClipInput.isNotBlank()) {
                            JanusClipboardBridge.push(mobileClipInput, "phone")
                            mobileClipInput = ""
                            scope.launch {
                                clipCardBorderFlash.animateTo(CyberAmber.copy(alpha = 0.9f), tween(70))
                                clipCardBorderFlash.animateTo(Color.Transparent, tween(160))
                            }
                            Toast.makeText(context, "Pushed to Desktop!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Push to Desktop Clipboard", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
