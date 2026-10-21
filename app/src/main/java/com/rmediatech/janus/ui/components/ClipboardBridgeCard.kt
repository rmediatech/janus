package com.rmediatech.janus.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmediatech.janus.bridge.BridgeState
import com.rmediatech.janus.clipboard.ClipboardItem
import com.rmediatech.janus.clipboard.JanusClipboardBridge
import com.rmediatech.janus.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Sovereign Bi-Directional Clipboard Bridge Card:
 * One Job: Renders latest synced clipboard item and provides instant phone-to-desktop push.
 */
@Composable
fun ClipboardBridgeCard(
    bridgeState: BridgeState,
    latestItem: ClipboardItem?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mobileClipInput by remember { mutableStateOf("") }
    val latestSyncedText = latestItem?.text ?: ""
    val clipCardBorderFlash = remember { Animatable(Color.Transparent) }

    LaunchedEffect(latestItem?.id) {
        if (latestItem != null) {
            val flashColor = if (latestItem.source == "desktop") CyberCyan else CyberAmber
            clipCardBorderFlash.animateTo(flashColor.copy(alpha = 0.9f), tween(70))
            clipCardBorderFlash.animateTo(Color.Transparent, tween(180))
        }
    }

    val clipBorderColor = when {
        clipCardBorderFlash.value != Color.Transparent -> clipCardBorderFlash.value
        latestItem != null -> CyberCyan.copy(alpha = 0.3f)
        else -> DarkCard
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, clipBorderColor)
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
