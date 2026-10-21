package com.rmediatech.janus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.rmediatech.janus.bridge.JanusBridgeService
import com.rmediatech.janus.logging.SovereignLogger
import com.rmediatech.janus.security.JanusPermissionHelper
import com.rmediatech.janus.ui.JanusMainScreen
import com.rmediatech.janus.ui.theme.JanusTheme

/**
 * Sovereign Janus Entry Point:
 * One Job: Thin Android ComponentActivity lifecycle shell (<= 80 lines).
 * Delegates permissions to JanusPermissionHelper and temporal wake probes to JanusBridgeService.
 */
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        JanusPermissionHelper.checkStorageManagerPermission(this)
    }

    override fun onResume() {
        super.onResume()
        JanusBridgeService.onTemporalResume(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SovereignLogger.init(applicationContext)
        SovereignLogger.i(SovereignLogger.Category.LIFECYCLE, "MainActivity.onCreate() — bootstrapping Janus")

        JanusPermissionHelper.requestRequiredPermissions(this, permissionLauncher)

        setContent {
            JanusTheme {
                val bridgeState by JanusBridgeService.bridgeState.collectAsState()
                JanusMainScreen(
                    bridgeState = bridgeState,
                    onToggleBridge = { enable ->
                        if (enable) {
                            JanusBridgeService.startService(this)
                        } else {
                            JanusBridgeService.stopService(this)
                        }
                    }
                )
            }
        }
    }
}
