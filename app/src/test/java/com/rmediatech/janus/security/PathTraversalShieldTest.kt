package com.rmediatech.janus.security

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PathTraversalShieldTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File

    @Before
    fun setup() {
        rootDir = tempFolder.newFolder("jail_root")
        File(rootDir, "documents").mkdir()
        File(rootDir, "documents/report.txt").writeText("secret data")
        File(rootDir, "photos").mkdir()
    }

    @Test
    fun testLegitimateRelativePathsResolve() {
        val safeFile = PathTraversalShield.resolveSafePath(rootDir, "documents/report.txt")
        assertNotNull(safeFile)
        assertTrue(safeFile!!.exists())
        assertEquals("report.txt", safeFile.name)

        val safeSubdir = PathTraversalShield.resolveSafePath(rootDir, "photos")
        assertNotNull(safeSubdir)
        assertTrue(safeSubdir!!.isDirectory)

        val rootSafe = PathTraversalShield.resolveSafePath(rootDir, "")
        assertNotNull(rootSafe)
        assertEquals(rootDir.canonicalPath, rootSafe!!.canonicalPath)
    }

    @Test
    fun testAdversarialTraversalPayloadsBlocked() {
        val maliciousPayloads = listOf(
            "../etc/passwd",
            "../../../../../../../../etc/shadow",
            "documents/../../etc/hosts",
            "..\\..\\windows\\system32",
            "%2e%2e%2fetc%2fpasswd",
            "%2e%2e/data/secret",
            "%2E%2E/system/build.prop",
            "documents/\u0000/../../escaped.txt",
            "documents/%00/escaped.txt",
            "/root/secret.key",
            "~/ssh/id_rsa",
            "photos/..\\..\\data",
            "\\\\attacker.com\\share\\payload"
        )

        for (payload in maliciousPayloads) {
            val result = PathTraversalShield.resolveSafePath(rootDir, payload)
            assertNull("Payload should have been blocked: $payload", result)
        }
    }

    @Test
    fun testSanitizeFileName() {
        assertEquals("normal.txt", PathTraversalShield.sanitizeFileName("normal.txt"))
        assertEquals("deep.pdf", PathTraversalShield.sanitizeFileName("folder/sub/deep.pdf"))
        assertEquals("escaped.jpg", PathTraversalShield.sanitizeFileName("..\\..\\escaped.jpg"))
        
        // Formula injection protection
        assertEquals("'=cmd|'calc'!A0.csv", PathTraversalShield.sanitizeFileName("=cmd|'calc'!A0.csv"))
        assertEquals("'=SUM(1+1).csv", PathTraversalShield.sanitizeFileName("=SUM(1+1).csv"))
        assertEquals("'+12345.xls", PathTraversalShield.sanitizeFileName("+12345.xls"))
        assertEquals("'-malicious.doc", PathTraversalShield.sanitizeFileName("-malicious.doc"))
        assertEquals("'@SUM(1+1)", PathTraversalShield.sanitizeFileName("@SUM(1+1)"))

        // Null bytes & controls stripped
        val withNull = "bad\u0000file.png"
        assertFalse(PathTraversalShield.sanitizeFileName(withNull).contains("\u0000"))

        // Empty fallback
        assertEquals("unnamed_file", PathTraversalShield.sanitizeFileName("   "))
    }
}
