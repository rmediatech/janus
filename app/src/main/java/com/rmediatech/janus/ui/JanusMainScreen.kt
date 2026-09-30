package com.rmediatech.janus.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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

@Composable
fun JanusMainScreen(
    bridgeState: BridgeState,
    onToggleBridge: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var mobileClipInput by remember { mutableStateOf("") }
    var latestSyncedText by remember { mutableStateOf(JanusClipboardBridge.getLatest()?.text ?: "") }

    DisposableEffect(Unit) {
        val listener: (com.rmediatech.janus.clipboard.ClipboardItem) -> Unit = { item ->
            latestSyncedText = item.text
        }
        JanusClipboardBridge.addListener(listener)
        onDispose {
            JanusClipboardBridge.removeListener(listener)
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

            // Status Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (bridgeState.isRunning) Color(0xFF10B981).copy(alpha = 0.2f) else Color.DarkGray.copy(alpha = 0.3f))
                    .border(
                        1.dp,
                        if (bridgeState.isRunning) Color(0xFF10B981) else Color.Gray,
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (bridgeState.isRunning) "ONLINE" else "OFFLINE",
                    color = if (bridgeState.isRunning) Color(0xFF10B981) else Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Master Activation Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (bridgeState.isRunning) CyberCyan.copy(alpha = 0.4f) else DarkCard)
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
                            text = if (bridgeState.isRunning) "Listening on port ${bridgeState.port}" else "Service stopped",
                            fontSize = 13.sp,
                            color = TextMuted
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Clipboard Bridge",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMain
                    )
                    Icon(Icons.Default.Sync, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Latest from desktop
                if (latestSyncedText.isNotBlank()) {
                    Text(text = "LATEST SYNCED ITEM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
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
