package com.rmediatech.sovereign.security

import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Universal Sovereign Sibling Attestation Protocol (SOVEREIGN-SIBLING-01)
 * Single Job: Cryptographic bloodline mutual recognition, anti-replay challenge verification,
 * and constant-time HMAC-SHA256 proof generation for Android mobile and smart TV clients.
 */
object SovereignSibling {
    /** Canonical Lineage Canary Preimage embedded in client bytecode */
    const val LINEAGE_CANARY_PREIMAGE: String = "SOVEREIGN-SIBLING-4E428019A109578B-RMD-2026-XUOXOD"

    /** Anti-replay temporal freshness ceiling (60 seconds) */
    const val MAX_TIMESTAMP_DRIFT_MS: Long = 60_000L

    const val HEADER_SIBLING_NAME: String = "x-sovereign-sibling-name"
    const val HEADER_SIBLING_NONCE: String = "x-sovereign-sibling-nonce"
    const val HEADER_SIBLING_TIMESTAMP: String = "x-sovereign-sibling-timestamp"
    const val HEADER_SIBLING_SIGNATURE: String = "x-sovereign-sibling-signature"
    const val HEADER_LINEAGE_STATUS: String = "x-sovereign-lineage"
    const val HEADER_LINEAGE_CALLER: String = "x-sovereign-sibling-caller"

    data class SiblingProof(
        val callerName: String,
        val nonce: String,
        val timestampMs: Long,
        val signature: String
    )

    data class SiblingChallenge(
        val nonce: String,
        val timestampMs: Long
    )

    sealed class SiblingResult {
        object Success : SiblingResult()
        data class Failure(val reason: String) : SiblingResult()
    }

    /**
     * Deterministically derives the 256-bit Lineage Key from Canary Preimage
     */
    fun deriveLineageKey(): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(LINEAGE_CANARY_PREIMAGE.toByteArray(Charsets.UTF_8))
    }

    /**
     * Issues challenge containing 16-byte hex nonce for local server pairing
     */
    fun issueChallenge(nowMs: Long = System.currentTimeMillis(), seedNonce: Long = 0L): SiblingChallenge {
        val digest = MessageDigest.getInstance("SHA-256")
        val bb = ByteBuffer.allocate(16)
        bb.putLong(seedNonce)
        bb.putLong(nowMs)
        digest.update(bb.array())
        digest.update("vox-android-sibling-challenge".toByteArray(Charsets.UTF_8))
        val hash = digest.digest()
        val nonce = hash.take(16).toByteArray().toHex()
        return SiblingChallenge(nonce, nowMs)
    }

    /**
     * Generates a valid cryptographic SiblingProof for mobile requests
     */
    fun generateProof(
        callerName: String,
        nonce: String,
        timestampMs: Long = System.currentTimeMillis()
    ): SiblingProof {
        val key = deriveLineageKey()
        val hmacBytes = computeHmac(key, callerName, nonce, timestampMs)
        return SiblingProof(
            callerName = callerName,
            nonce = nonce,
            timestampMs = timestampMs,
            signature = hmacBytes.toHex()
        )
    }

    /**
     * Verifies SiblingProof mathematically, temporally, and in constant-time
     */
    fun verifyProof(proof: SiblingProof, nowMs: Long = System.currentTimeMillis()): SiblingResult {
        if (proof.callerName.isBlank()) {
            return SiblingResult.Failure("Caller name cannot be blank")
        }

        val deltaMs = Math.abs(nowMs - proof.timestampMs)
        if (deltaMs > MAX_TIMESTAMP_DRIFT_MS) {
            return SiblingResult.Failure("Timestamp drift ${deltaMs}ms exceeds ceiling ${MAX_TIMESTAMP_DRIFT_MS}ms")
        }

        val receivedBytes = proof.signature.hexToBytes()
            ?: return SiblingResult.Failure("Malformed signature hex")
        if (receivedBytes.size != 32) {
            return SiblingResult.Failure("Invalid signature byte length")
        }

        val key = deriveLineageKey()
        val expectedBytes = computeHmac(key, proof.callerName, proof.nonce, proof.timestampMs)

        return if (MessageDigest.isEqual(receivedBytes, expectedBytes)) {
            SiblingResult.Success
        } else {
            SiblingResult.Failure("Signature mismatch: peer does not share sovereign bloodline")
        }
    }

    /**
     * Builds HTTP header map for attaching to standard HTTP/OkHttp requests
     */
    fun buildHeaders(proof: SiblingProof): Map<String, String> {
        return mapOf(
            HEADER_SIBLING_NAME to proof.callerName,
            HEADER_SIBLING_NONCE to proof.nonce,
            HEADER_SIBLING_TIMESTAMP to proof.timestampMs.toString(),
            HEADER_SIBLING_SIGNATURE to proof.signature
        )
    }

    /**
     * Computes RFC 2104 HMAC-SHA256: caller || nonce || timestamp_be_bytes
     */
    fun computeHmac(key: ByteArray, callerName: String, nonce: String, timestampMs: Long): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        mac.update(callerName.toByteArray(Charsets.UTF_8))
        mac.update(nonce.toByteArray(Charsets.UTF_8))
        val bb = ByteBuffer.allocate(8)
        bb.putLong(timestampMs)
        mac.update(bb.array())
        return mac.doFinal()
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun String.hexToBytes(): ByteArray? {
        if (length % 2 != 0) return null
        val result = ByteArray(length / 2)
        try {
            for (i in indices step 2) {
                result[i / 2] = substring(i, i + 2).toInt(16).toByte()
            }
            return result
        } catch (_: Exception) {
            return null
        }
    }
}
