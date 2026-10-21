package com.rmediatech.janus.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rmediatech.janus.bridge.BridgeState
import com.rmediatech.janus.clipboard.JanusClipboardBridge
import com.rmediatech.janus.ui.components.*
import com.rmediatech.janus.ui.theme.DarkCyberBg

/**
 * Sovereign Janus Main Screen:
 * One Job: Orchestrates JanusHeaderBar, BridgeServerCard, ClipboardBridgeCard,
 * and modal dialogs with strict OJP modularity.
 */
@Composable
fun JanusMainScreen(
    bridgeState: BridgeState,
    onToggleBridge: (Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    val latestItem by JanusClipboardBridge.latestState.collectAsState()
    var showAboutDialog by remember { mutableStateOf(false) }
    var showDiagnosticLogsDialog by remember { mutableStateOf(false) }

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

    val pairingUrl = remember(bridgeState.isRunning, bridgeState.localIp, bridgeState.port, bridgeState.sessionSecret) {
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
        AboutJanusDialog(
            onDismiss = { showAboutDialog = false },
            onOpenDiagnosticLogs = { showDiagnosticLogsDialog = true }
        )
    }

    if (showDiagnosticLogsDialog) {
        DiagnosticLogsDialog(
            onDismiss = { showDiagnosticLogsDialog = false }
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
        JanusHeaderBar(
            bridgeState = bridgeState,
            pulseAlpha = pulseAlpha,
            onShowAbout = { showAboutDialog = true }
        )

        Spacer(modifier = Modifier.height(24.dp))

        BridgeServerCard(
            bridgeState = bridgeState,
            qrBitmap = qrBitmap,
            onToggleBridge = onToggleBridge
        )

        Spacer(modifier = Modifier.height(20.dp))

        ClipboardBridgeCard(
            bridgeState = bridgeState,
            latestItem = latestItem
        )
    }
}
