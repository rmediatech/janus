package com.rmediatech.janus.security

import org.junit.Assert.*
import org.junit.Test

class JanusAuthTokenTest {

    @Test
    fun testTokenAndPinGeneration() {
        val auth = JanusAuthToken()
        assertEquals(64, auth.sessionSecret.length) // 32 bytes hex = 64 chars
        assertTrue(auth.pairingPin.startsWith("JNX-"))
        assertEquals(8, auth.pairingPin.length) // "JNX-XXXX"
    }

    @Test
    fun testAuthenticationSuccess() {
        val auth = JanusAuthToken()
        // Test secret match
        assertTrue(auth.authenticate(auth.sessionSecret))

        // Test PIN match (case-insensitive)
        assertTrue(auth.authenticate(auth.pairingPin.lowercase()))
        assertTrue(auth.authenticate(auth.pairingPin.uppercase()))
    }

    @Test
    fun testBruteForceLockout() {
        val auth = JanusAuthToken()
        
        // 4 failed attempts should not lock
        for (i in 1..4) {
            assertFalse(auth.authenticate("WRONG-$i"))
            assertFalse(auth.isLocked())
        }

        // 5th failed attempt triggers lockout
        assertFalse(auth.authenticate("WRONG-5"))
        assertTrue(auth.isLocked())

        // Even correct credentials must fail while locked
        assertFalse(auth.authenticate(auth.sessionSecret))
        assertFalse(auth.authenticate(auth.pairingPin))

        // Manual reset clears lock
        auth.resetLock()
        assertFalse(auth.isLocked())
        assertTrue(auth.authenticate(auth.sessionSecret))
    }

    @Test
    fun testNullOrEmptyRejection() {
        val auth = JanusAuthToken()
        assertFalse(auth.authenticate(null))
        assertFalse(auth.authenticate(""))
        assertFalse(auth.authenticate("   "))
    }
}
