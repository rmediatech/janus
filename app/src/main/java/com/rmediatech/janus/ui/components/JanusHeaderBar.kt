package com.rmediatech.janus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmediatech.janus.bridge.BridgeState
import com.rmediatech.janus.ui.theme.CyberCyan
import com.rmediatech.janus.ui.theme.TextMuted

/**
 * Sovereign Janus Header Bar:
 * One Job: Renders application identity, live connection status badge, and info trigger.
 */
@Composable
fun JanusHeaderBar(
    bridgeState: BridgeState,
    pulseAlpha: Float,
    onShowAbout: () -> Unit
) {
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            val badgeBg: Color
            val badgeBorder: Color
            val badgeTextColor: Color
            val badgeLabel: String
            when {
                !bridgeState.isRunning -> {
                    badgeBg = Color.DarkGray.copy(alpha = 0.25f)
                    badgeBorder = Color.Gray.copy(alpha = 0.4f)
                    badgeTextColor = Color.LightGray
                    badgeLabel = "OFFLINE"
                }
                bridgeState.connectedClients > 0 -> {
                    badgeBg = Color(0xFF10B981).copy(alpha = 0.15f)
                    badgeBorder = Color(0xFF10B981)
                    badgeTextColor = Color(0xFF10B981)
                    badgeLabel = if (bridgeState.connectedClients == 1) "DESKTOP LINKED" else "${bridgeState.connectedClients} LINKED"
                }
                else -> {
                    badgeBg = CyberCyan.copy(alpha = 0.12f)
                    badgeBorder = CyberCyan.copy(alpha = 0.5f)
                    badgeTextColor = CyberCyan
                    badgeLabel = "STANDBY"
                }
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
                onClick = onShowAbout,
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
}
