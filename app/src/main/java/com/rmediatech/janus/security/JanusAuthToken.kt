package com.rmediatech.janus.security

import android.content.Context
import com.rmediatech.janus.logging.SovereignLogger
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sovereign Workstation Identity Anchor (`SOVEREIGN-IDENTITY-ANCHOR-01`):
 * Tracks paired workstation identity across transient Layer-3 IP lease changes.
 */
data class WorkstationAnchor(
    val clientId: String,
    val label: String,
    val lastKnownIp: String,
    val pairedAtMs: Long,
    val lastSeenAtMs: Long
)

/**
 * JanusAuthToken manages authentication session credentials, anti-brute-force rate limiting,
 * and hardware/cryptographic identity anchoring across transient IP changes.
 */
class JanusAuthToken(
     sessionSecretInit: String = generateSecretHex(32),
     pairingPinInit: String = generatePairingPin()
) {
    @Volatile
    var sessionSecret: String = sessionSecretInit
        private set

    @Volatile
    var pairingPin: String = pairingPinInit
        private set

    private val failedAttempts = AtomicInteger(0)
    private var lockUntilTimestamp: Long = 0L
    private val anchoredWorkstations = ConcurrentHashMap<String, WorkstationAnchor>()

    companion object {
        private const val PREFS_NAME = "janus_sovereign_auth"
        private const val KEY_SECRET = "session_secret"
        private const val KEY_PIN = "pairing_pin"
        private val RANDOM = SecureRandom()
        private const val PIN_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" // Omit ambiguous 0,1,I,O

        fun generateSecretHex(byteCount: Int = 32): String {
            val bytes = ByteArray(byteCount)
            RANDOM.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun generatePairingPin(): String {
            val prefix = "JNX"
            val sb = StringBuilder()
            for (i in 0 until 4) {
                val index = RANDOM.nextInt(PIN_ALPHABET.length)
                sb.append(PIN_ALPHABET[index])
            }
            return "$prefix-$sb"
        }
    }

    /**
     * Restores or persists the cryptographic session secret and pairing PIN in local device storage
     * so paired workstations survive service restarts and transient network handoffs.
     */
    @Synchronized
    fun initPersistence(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedSecret = prefs.getString(KEY_SECRET, null)
            val savedPin = prefs.getString(KEY_PIN, null)
            if (!savedSecret.isNullOrBlank() && savedSecret.length == 64 && !savedPin.isNullOrBlank()) {
                sessionSecret = savedSecret
                pairingPin = savedPin
                SovereignLogger.i(SovereignLogger.Category.SECURITY, "Restored anchored pairing credentials ($pairingPin)")
            } else {
                prefs.edit()
                    .putString(KEY_SECRET, sessionSecret)
                    .putString(KEY_PIN, pairingPin)
                    .apply()
                SovereignLogger.i(SovereignLogger.Category.SECURITY, "Persisted new pairing credentials ($pairingPin)")
            }
        } catch (_: Exception) {}
    }

    /**
     * Verifies an incoming authorization token or PIN code.
     * Enforces anti-brute-force lockout after repeated failures.
     */
    @Synchronized
    fun authenticate(candidate: String?, clientId: String? = null, clientIp: String? = null): Boolean {
        if (candidate.isNullOrBlank() || candidate.length > 256) return false

        val now = System.currentTimeMillis()
        if (now < lockUntilTimestamp) {
            SovereignLogger.w(SovereignLogger.Category.SECURITY, "Authentication rejected: brute-force lockout active")
            return false
        }

        val trimmed = candidate.trim()
        val isSecretMatch = trimmed.equals(sessionSecret, ignoreCase = false)
        val isPinMatch = trimmed.equals(pairingPin, ignoreCase = true)

        if (isSecretMatch || isPinMatch) {
            failedAttempts.set(0)
            if (!clientId.isNullOrBlank() && !clientIp.isNullOrBlank()) {
                anchorWorkstation(clientId, clientIp)
            }
            return true
        } else {
            val count = failedAttempts.incrementAndGet()
            if (count >= 5) {
                lockUntilTimestamp = now + 15_000L
                SovereignLogger.w(SovereignLogger.Category.SECURITY, "Brute-force threshold reached (5 failures); locked for 15s")
            }
            return false
        }
    }

    /**
     * Anchors a workstation's cryptographic/hardware client ID across transient IP drift
     * (`SOVEREIGN-IDENTITY-ANCHOR-01`).
     */
    fun anchorWorkstation(clientId: String, currentIp: String, label: String = "Workstation"): WorkstationAnchor {
        val cleanId = SovereignSanitizer.sanitizeAnsi(clientId, 64)
        val cleanIp = SovereignSanitizer.sanitizeAnsi(currentIp, 45)
        val cleanLabel = SovereignSanitizer.sanitizeFormulaField(label, 64).ifEmpty { "Workstation" }
        val now = System.currentTimeMillis()

        val existing = anchoredWorkstations[cleanId]
        val updated = if (existing != null) {
            if (existing.lastKnownIp != cleanIp) {
                SovereignLogger.i(
                    SovereignLogger.Category.SECURITY,
                    "Workstation [$cleanId] IP drift healed: ${existing.lastKnownIp} -> $cleanIp"
                )
            }
            existing.copy(lastKnownIp = cleanIp, lastSeenAtMs = now)
        } else {
            SovereignLogger.i(
                SovereignLogger.Category.SECURITY,
                "Anchored new workstation [$cleanId] at $cleanIp"
            )
            WorkstationAnchor(cleanId, cleanLabel, cleanIp, now, now)
        }
        anchoredWorkstations[cleanId] = updated
        return updated
    }

    fun getAnchoredWorkstation(clientId: String): WorkstationAnchor? = anchoredWorkstations[clientId]

    fun getAllAnchoredWorkstations(): List<WorkstationAnchor> = anchoredWorkstations.values.toList()

    fun isLocked(): Boolean = System.currentTimeMillis() < lockUntilTimestamp

    fun resetLock() {
        failedAttempts.set(0)
        lockUntilTimestamp = 0L
    }
}
