package com.rmediatech.janus.security

import com.rmediatech.sovereign.security.SovereignSibling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Universal Sovereign Sibling Adversarial Test Battery (SOVEREIGN-SIBLING-01)
 * Asserts cross-platform wire compatibility between Android Kotlin and Rust Phylax/Propylea.
 */
class AdversarialSovereignSiblingTest {

    @Test
    fun testSiblingProofLifecycleAndVerification() {
        val nowMs = 1700000000000L
        val challenge = SovereignSibling.issueChallenge(nowMs, 1337L)
        assertEquals(32, challenge.nonce.length) // 16 bytes = 32 hex chars

        val proof = SovereignSibling.generateProof("janus", challenge.nonce, nowMs)
        assertEquals("janus", proof.callerName)
        assertEquals(challenge.nonce, proof.nonce)
        assertEquals(nowMs, proof.timestampMs)
        assertEquals(64, proof.signature.length) // 32 bytes = 64 hex chars

        val result = SovereignSibling.verifyProof(proof, nowMs + 500L)
        assertTrue("Proof should be verified successfully", result is SovereignSibling.SiblingResult.Success)
    }

    @Test
    fun testStaleTimestampReplayRejection() {
        val nowMs = 1700000000000L
        val staleProof = SovereignSibling.generateProof("janus", "nonce-1", nowMs - 60001L)

        val result = SovereignSibling.verifyProof(staleProof, nowMs)
        assertTrue("Stale proof must fail", result is SovereignSibling.SiblingResult.Failure)
        val reason = (result as SovereignSibling.SiblingResult.Failure).reason
        assertTrue("Reason should cite ceiling", reason.contains("exceeds ceiling"))
    }

    @Test
    fun testTamperedSignatureRejection() {
        val nowMs = 1700000000000L
        val proof = SovereignSibling.generateProof("janus", "nonce-2", nowMs)
        val tampered = proof.copy(signature = "00".repeat(32))

        val result = SovereignSibling.verifyProof(tampered, nowMs)
        assertTrue("Tampered signature must fail", result is SovereignSibling.SiblingResult.Failure)
    }

    @Test
    fun testHeaderGenerationWireParity() {
        val nowMs = 1700000000000L
        val proof = SovereignSibling.generateProof("argus", "wire-nonce", nowMs)
        val headers = SovereignSibling.buildHeaders(proof)

        assertEquals("argus", headers[SovereignSibling.HEADER_SIBLING_NAME])
        assertEquals("wire-nonce", headers[SovereignSibling.HEADER_SIBLING_NONCE])
        assertEquals(nowMs.toString(), headers[SovereignSibling.HEADER_SIBLING_TIMESTAMP])
        assertEquals(proof.signature, headers[SovereignSibling.HEADER_SIBLING_SIGNATURE])
    }

    @Test
    fun testZeroPanicFuzzing() {
        val nowMs = 1700000000000L
        val fuzzCases = listOf(
            "",
            "1",
            "xyz",
            "deadbeef",
            "\u0000\u0001\u0002",
            "ab".repeat(64)
        )

        for (case in fuzzCases) {
            val proof = SovereignSibling.SiblingProof(
                callerName = "fuzz",
                nonce = "nonce",
                timestampMs = nowMs,
                signature = case
            )
            val result = SovereignSibling.verifyProof(proof, nowMs)
            assertTrue("Fuzz case '$case' must fail safely", result is SovereignSibling.SiblingResult.Failure)
        }

        // Blank caller name
        val blankProof = SovereignSibling.SiblingProof(
            callerName = "   ",
            nonce = "nonce",
            timestampMs = nowMs,
            signature = "aa".repeat(32)
        )
        val blankResult = SovereignSibling.verifyProof(blankProof, nowMs)
        assertTrue("Blank caller must fail", blankResult is SovereignSibling.SiblingResult.Failure)
    }
}
