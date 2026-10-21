package com.rmediatech.janus.security

import com.rmediatech.janus.bridge.JanusWebSocketManager
import com.rmediatech.janus.clipboard.JanusClipboardBridge
import com.rmediatech.janus.logging.SovereignLogger
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Sovereign Adversarial Security Test Suite for Janus (TDD++++++++++):
 * Validates resilience against formula injection, ANSI terminal/log poisoning,
 * allocation bombs, path traversal breakouts, corrupted IP/port inputs,
 * workstation identity anchoring across IP drift, and 3D temporal lifecycle transitions.
 */
class AdversarialJanusSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var jailRoot: File

    @Before
    fun setup() {
        jailRoot = tempFolder.newFolder("janus_jail")
        JanusClipboardBridge.clearHistory()
        SovereignLogger.clearLogs()
    }

    // --- THREAT MODEL 1: FORMULA & CODE INJECTION ---

    @Test
    fun testFormulaInjectionAllPrefixesNeutralized() {
        val eqPayload = "=cmd|' /C calc'!A0.csv"
        val plusPayload = "+12345*cmd|'calc'!A0.xls"
        val minusPayload = "-2+3*[calc].csv"
        val atPayload = "@SUM(1+1)*cmd|' /C calc'!A0"
        val tabPayload = "\t=DDE(\"cmd\";\"/C calc\";\"__!A0\").csv"
        val crPayload = "\r=1+1.csv"

        assertTrue(PathTraversalShield.sanitizeFileName(eqPayload).startsWith("'="))
        assertTrue(PathTraversalShield.sanitizeFileName(plusPayload).startsWith("'+"))
        assertTrue(PathTraversalShield.sanitizeFileName(minusPayload).startsWith("'-"))
        assertTrue(PathTraversalShield.sanitizeFileName(atPayload).startsWith("'@"))
        assertTrue(PathTraversalShield.sanitizeFileName(tabPayload).startsWith("'"))
        assertTrue(PathTraversalShield.sanitizeFileName(crPayload).startsWith("'"))

        assertTrue(SovereignSanitizer.sanitizeFormulaField(eqPayload).startsWith("'="))
        assertTrue(SovereignSanitizer.sanitizeFormulaField(plusPayload).startsWith("'+"))
        assertTrue(SovereignSanitizer.sanitizeFormulaField(minusPayload).startsWith("'-"))
        assertTrue(SovereignSanitizer.sanitizeFormulaField(atPayload).startsWith("'@"))
        assertTrue(SovereignSanitizer.sanitizeFormulaField(tabPayload).startsWith("'"))
        assertTrue(SovereignSanitizer.sanitizeFormulaField(crPayload).startsWith("'"))
    }

    @Test
    fun testAnsiDisguisedFormulaInjectionNeutralized() {
        // Attacker prefixes formula with ANSI CSI color sequence hoping stripping \x1B leaves "[31m=cmd" or similar
        val disguised = "\u001B[31;1m=cmd|'calc'!A0.csv\u001B[0m"
        val sanitizedFile = PathTraversalShield.sanitizeFileName(disguised)
        assertEquals("'=cmd|'calc'!A0.csv", sanitizedFile)

        val sanitizedField = SovereignSanitizer.sanitizeFormulaField(disguised)
        assertEquals("'=cmd|'calc'!A0.csv", sanitizedField)
    }

    // --- THREAT MODEL 2: ANSI TERMINAL & LOG STREAM POISONING ---

    @Test
    fun testAnsiCsiAndOscSequencesScrubbedEverywhere() {
        val csiWipe = "\u001B[2J\u001B[HMalicious Banner\u001B[0m"
        val oscTitle = "\u001B]0;Pwned Terminal\u0007Clean Content"
        val controlBytes = " Good\u0000Text\u0007With\u0008Controls\u001F "

        assertEquals("Malicious Banner", SovereignSanitizer.sanitizeAnsi(csiWipe))
        assertEquals("Clean Content", SovereignSanitizer.sanitizeAnsi(oscTitle))
        assertEquals("GoodTextWithControls", SovereignSanitizer.sanitizeAnsi(controlBytes))
    }

    // --- THREAT MODEL 3: ALLOCATION BOMBS & MEMORY DOS ---

    @Test
    fun testExtremeFilenameAllocationBombBoundedWithExtensionPreserved() {
        val hugeName = "A".repeat(10_000) + ".apk"
        val sanitized = PathTraversalShield.sanitizeFileName(hugeName)
        assertEquals(SovereignSanitizer.MAX_FILENAME_LENGTH, sanitized.length)
        assertTrue("Extension must be preserved after truncation", sanitized.endsWith(".apk"))
    }

    @Test
    fun testExtremeClipboardAllocationBombBounded() {
        val massivePayload = "X".repeat(200_000)
        val item = JanusClipboardBridge.push(massivePayload, "desktop")
        assertNotNull(item)
        assertEquals(SovereignSanitizer.MAX_CLIPBOARD_BYTES, item!!.text.length)
    }

    @Test
    fun testExtremePathLengthTraversalBombRejected() {
        val deepBomb = "subdir/".repeat(500) + "file.txt"
        assertTrue(deepBomb.length > SovereignSanitizer.MAX_PATH_LENGTH)
        assertNull("Overlong path bomb must be rejected immediately", PathTraversalShield.resolveSafePath(jailRoot, deepBomb))
    }

    @Test
    fun testOversizedAuthCandidateRejected() {
        val auth = JanusAuthToken()
        val bombToken = "a".repeat(10_000)
        assertFalse(auth.authenticate(bombToken))
    }

    // --- THREAT MODEL 4: DIRECTORY TRAVERSAL & JAIL BREAKOUT ---

    @Test
    fun testDoubleEncodedAndNullByteTraversalBlocked() {
        val payloads = listOf(
            "../etc/passwd",
            "..\\..\\system32\\cmd.exe",
            "%2e%2e%2fetc%2fpasswd",
            "%252e%252e%252fetc%252fshadow",
            "valid/\u0000/../../etc/hosts",
            "valid/%00/../../etc/hosts",
            "/etc/passwd",
            "~/id_rsa",
            "\\\\10.0.0.1\\c$\\Windows"
        )
        for (p in payloads) {
            assertNull("Traversal payload must be blocked: $p", PathTraversalShield.resolveSafePath(jailRoot, p))
        }
    }

    // --- THREAT MODEL 5: STRICT IPV4 & PORT BOUNDARY VALIDATION ---

    @Test
    fun testStrictIpv4AndPortValidation() {
        assertTrue(SovereignSanitizer.isValidIpv4("192.0.2.1"))
        assertTrue(SovereignSanitizer.isValidIpv4("10.0.0.42"))
        assertTrue(SovereignSanitizer.isValidIpv4("172.16.0.254"))

        assertFalse(SovereignSanitizer.isValidIpv4("192.0.2.256"))
        assertFalse(SovereignSanitizer.isValidIpv4("192.0.2.01")) // Octal ambiguity rejected
        assertFalse(SovereignSanitizer.isValidIpv4("192.0.2.-1"))
        assertFalse(SovereignSanitizer.isValidIpv4("192.0.2.1; id"))
        assertFalse(SovereignSanitizer.isValidIpv4(""))

        assertTrue(SovereignSanitizer.isValidPort(8080))
        assertTrue(SovereignSanitizer.isValidPort(1))
        assertTrue(SovereignSanitizer.isValidPort(65535))
        assertFalse(SovereignSanitizer.isValidPort(0))
        assertFalse(SovereignSanitizer.isValidPort(-80))
        assertFalse(SovereignSanitizer.isValidPort(65536))
    }

    // --- THREAT MODEL 6: HARDWARE/CRYPTOGRAPHIC IDENTITY ANCHORING (SOVEREIGN-IDENTITY-ANCHOR-01) ---

    @Test
    fun testWorkstationIdentitySurvivesTransientIpDrift() {
        val auth = JanusAuthToken()
        val clientId = "workstation_ed25519_7f8a9b0c"

        // Initial pairing at 10.0.0.50
        assertTrue(auth.authenticate(auth.pairingPin, clientId = clientId, clientIp = "10.0.0.50"))
        val initialAnchor = auth.getAnchoredWorkstation(clientId)
        assertNotNull(initialAnchor)
        assertEquals("10.0.0.50", initialAnchor!!.lastKnownIp)

        // Workstation DHCP lease drifts to 10.0.0.142
        assertTrue(auth.authenticate(auth.sessionSecret, clientId = clientId, clientIp = "10.0.0.142"))
        val healedAnchor = auth.getAnchoredWorkstation(clientId)
        assertNotNull(healedAnchor)
        assertEquals("Workstation IP must seamlessly heal across DHCP lease drift", "10.0.0.142", healedAnchor!!.lastKnownIp)
        assertEquals(initialAnchor.pairedAtMs, healedAnchor.pairedAtMs)
    }

    // --- THREAT MODEL 7: 3D TEMPORAL LIFECYCLE & ZERO-GHOST-SOCKET (SOVEREIGN-LIFECYCLE-VIABILITY-01) ---

    @Test
    fun testWebSocketManagerTemporalPruneAndCleanState() {
        var latestClientCount = -1
        var latestEvent = ""
        val manager = JanusWebSocketManager(
            onClientCountChanged = { latestClientCount = it },
            onActivityEvent = { latestEvent = it }
        )

        assertEquals(0, manager.activeClientCount)
        assertEquals(0, manager.pruneAndProbeConnections())
        manager.broadcast("""{"type":"ping"}""")
        manager.closeAll()
        assertEquals(0, manager.activeClientCount)
    }
}
