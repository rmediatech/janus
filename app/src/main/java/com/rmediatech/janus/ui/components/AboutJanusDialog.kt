package com.rmediatech.janus.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmediatech.janus.ui.theme.*

/**
 * Sovereign About Janus Dialog:
 * One Job: Presents Janus version, architecture overview, and RMediaTech provenance.
 */
@Composable
fun AboutJanusDialog(
    onDismiss: () -> Unit,
    onOpenDiagnosticLogs: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                onOpenDiagnosticLogs()
            }) {
                Text("Diagnostic Logs", color = CyberAmber, fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = CyberCyan, fontWeight = FontWeight.Bold)
            }
        }
    )
}
