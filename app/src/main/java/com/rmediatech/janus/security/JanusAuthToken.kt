package com.rmediatech.janus.security

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger

/**
 * JanusAuthToken manages authentication session credentials and anti-brute-force rate limiting.
 */
class JanusAuthToken(
    val sessionSecret: String = generateSecretHex(32),
    val pairingPin: String = generatePairingPin()
) {
    private val failedAttempts = AtomicInteger(0)
    private var lockUntilTimestamp: Long = 0L

    companion object {
        private val RANDOM = SecureRandom()
        private const val PIN_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" // Omit ambiguous 0,1,I,O

        fun generateSecretHex(byteCount: Int = 32): String {
            val bytes = ByteArray(byteCount)
            RANDOM.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun generatePairingPin(): String {
            val prefix = "JNX"
            val sb = java.lang.StringBuilder()
            for (i in 0 until 4) {
                val index = RANDOM.nextInt(PIN_ALPHABET.length)
                sb.append(PIN_ALPHABET[index])
            }
            return "$prefix-$sb"
        }
    }

    /**
     * Verifies an incoming authorization token or PIN code.
     * Enforces anti-brute-force lockout after repeated failures.
     */
    @Synchronized
    fun authenticate(candidate: String?): Boolean {
        if (candidate.isNullOrBlank()) return false

        val now = System.currentTimeMillis()
        if (now < lockUntilTimestamp) {
            return false // Locked out due to brute force
        }

        val trimmed = candidate.trim()
        val isSecretMatch = trimmed.equals(sessionSecret, ignoreCase = false)
        val isPinMatch = trimmed.equals(pairingPin, ignoreCase = true)

        if (isSecretMatch || isPinMatch) {
            failedAttempts.set(0)
            return true
        } else {
            val count = failedAttempts.incrementAndGet()
            if (count >= 5) {
                // Lockout for 15 seconds after 5 failed attempts
                lockUntilTimestamp = now + 15_000L
            }
            return false
        }
    }

    fun isLocked(): Boolean {
        return System.currentTimeMillis() < lockUntilTimestamp
    }

    fun resetLock() {
        failedAttempts.set(0)
        lockUntilTimestamp = 0L
    }
}
