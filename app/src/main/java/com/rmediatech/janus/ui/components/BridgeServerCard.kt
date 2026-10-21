package com.rmediatech.janus.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmediatech.janus.bridge.BridgeState
import com.rmediatech.janus.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Sovereign Bridge Server Control & Pairing Card:
 * One Job: Renders bridge power toggle, browser URL, pairing PIN, and instant QR code.
 */
@Composable
fun BridgeServerCard(
    bridgeState: BridgeState,
    qrBitmap: ImageBitmap?,
    onToggleBridge: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val serverCardBorderFlash = remember { Animatable(Color.Transparent) }
    val urlBoxBorderFlash = remember { Animatable(Color.Transparent) }
    val pinBoxBorderFlash = remember { Animatable(Color.Transparent) }

    LaunchedEffect(bridgeState.connectedClients) {
        if (bridgeState.connectedClients > 0) {
            serverCardBorderFlash.animateTo(Color(0xFF10B981).copy(alpha = 0.9f), tween(80))
            serverCardBorderFlash.animateTo(Color.Transparent, tween(200))
        }
    }

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
        border = BorderStroke(1.dp, serverBorderColor)
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
}
