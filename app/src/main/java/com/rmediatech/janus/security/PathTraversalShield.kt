package com.rmediatech.janus.security

import java.io.File

/**
 * PathTraversalShield enforces strict jail boundaries around allowed storage roots.
 * Neutralizes relative path escapes (../, ..\), null byte truncations (%00),
 * URL-encoded traversals, symlink breakout, ANSI poisoning, and allocation bombs.
 */
object PathTraversalShield {

    private val SUSPICIOUS_PATTERNS = listOf(
        "..",
        "%2e%2e",
        "%2E%2E",
        "%252e",
        "\u0000",
        "%00",
        "\\\\",
        "~"
    )

    /**
     * Resolves and verifies that [requestedPath] remains strictly within [allowedRoot].
     * Returns canonical [File] if safe, or null if traversal attempt or allocation bomb is detected.
     */
    fun resolveSafePath(allowedRoot: File, requestedPath: String): File? {
        if (requestedPath.length > SovereignSanitizer.MAX_PATH_LENGTH) {
            return null
        }

        val rootCanonical = try {
            allowedRoot.canonicalFile
        } catch (_: Exception) {
            return null
        }

        // Check for raw traversal patterns in input string
        val normalizedInput = requestedPath.replace('\\', '/')
        for (pattern in SUSPICIOUS_PATTERNS) {
            if (requestedPath.contains(pattern, ignoreCase = true) ||
                normalizedInput.contains(pattern, ignoreCase = true)
            ) {
                return null
            }
        }

        // Reject absolute path attempts (e.g. /etc/passwd or /root/...)
        if (normalizedInput.startsWith("/") && normalizedInput.length > 1) {
            return null
        }

        // Clean leading slashes to prevent absolute root escapes
        val cleanRelative = normalizedInput.trimStart('/')
        val target = File(rootCanonical, cleanRelative)

        return try {
            val targetCanonical = target.canonicalFile
            val rootPath = rootCanonical.path
            val targetPath = targetCanonical.path

            // Assert that canonical path starts with canonical root path
            if (targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)) {
                targetCanonical
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Sanitizes an uploaded file name to prevent directory escape, ANSI log poisoning,
     * spreadsheet formula injection, and length allocation bombs.
     */
    fun sanitizeFileName(fileName: String): String {
        val rawTrimmedCheck = fileName.dropWhile { it == ' ' }
        val rawStartsFormula = rawTrimmedCheck.firstOrNull()?.let {
            it == '=' || it == '+' || it == '-' || it == '@' || it == '\t' || it == '\r'
        } ?: false

        // Strip client directory paths (e.g. folder/sub/file.txt or C:\path\file.txt)
        // Only treat '/' or '\' as directory separators if the string does not start with a formula prefix
        val baseName = if (rawStartsFormula) {
            fileName.replace('\\', '_').replace('/', '_')
        } else {
            fileName.replace('\\', '/').substringAfterLast('/')
        }

        val hadLeadingTabOrCr = rawStartsFormula || baseName.startsWith('\t') || baseName.startsWith('\r')

        // Preserve trailing extension before bounding length against allocation bombs
        val rawDotIdx = baseName.lastIndexOf('.')
        val preservedExt = if (rawDotIdx in 1 until baseName.length - 1 && (baseName.length - rawDotIdx) <= 16) {
            baseName.substring(rawDotIdx).replace(Regex("[^a-zA-Z0-9._-]"), "")
        } else ""

        // Strip full ANSI escape sequences first, then control characters & null bytes
        var clean = SovereignSanitizer.sanitizeAnsi(baseName, maxLength = 1024)
        clean = clean.replace(Regex("[\\x00-\\x1F\\x7F]"), "")
        clean = clean.replace("..", "_").trim()
        if (clean.isEmpty()) return "unnamed_file"

        // Formula injection protection for CSV/spreadsheet/table contexts
        val first = clean[0]
        if (hadLeadingTabOrCr || first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            if (!clean.startsWith("'")) {
                clean = "'$clean"
            }
        }

        // Enforce 255-byte POSIX filename ceiling while preserving extension if present
        if (clean.length > SovereignSanitizer.MAX_FILENAME_LENGTH) {
            clean = if (preservedExt.isNotEmpty() && preservedExt.length < SovereignSanitizer.MAX_FILENAME_LENGTH) {
                clean.substring(0, SovereignSanitizer.MAX_FILENAME_LENGTH - preservedExt.length) + preservedExt
            } else {
                clean.substring(0, SovereignSanitizer.MAX_FILENAME_LENGTH)
            }
        }
        return clean
    }
}
