package com.rmediatech.janus.security

import java.io.File

/**
 * PathTraversalShield enforces strict jail boundaries around allowed storage roots.
 * Neutralizes relative path escapes (../, ..\), null byte truncations (%00),
 * URL-encoded traversals, and symlink breakout.
 */
object PathTraversalShield {

    private val SUSPICIOUS_PATTERNS = listOf(
        "..",
        "%2e%2e",
        "%2E%2E",
        "\u0000",
        "%00",
        "\\\\",
        "~"
    )

    /**
     * Resolves and verifies that [requestedPath] remains strictly within [allowedRoot].
     * Returns canonical [File] if safe, or null if traversal attempt is detected.
     */
    fun resolveSafePath(allowedRoot: File, requestedPath: String): File? {
        val rootCanonical = try {
            allowedRoot.canonicalFile
        } catch (_: Exception) {
            return null
        }

        // Check for raw traversal patterns in input string
        val normalizedInput = requestedPath.replace('\\', '/')
        for (pattern in SUSPICIOUS_PATTERNS) {
            if (requestedPath.contains(pattern, ignoreCase = true) || 
                normalizedInput.contains(pattern, ignoreCase = true)) {
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
     * Sanitizes an uploaded file name to prevent directory escape or dangerous characters.
     */
    fun sanitizeFileName(fileName: String): String {
        // Strip client directory paths (e.g. folder/sub/file.txt or C:\path\file.txt)
        val baseName = fileName.replace('\\', '/').substringAfterLast('/')
        // Strip control characters, ANSI escape codes, and null bytes
        var clean = baseName.replace(Regex("[\\x00-\\x1F\\x7F]"), "")
        // Prevent dot-dot
        clean = clean.replace("..", "_")
        // Formula injection protection for CSV/listing contexts
        if (clean.startsWith("=") || clean.startsWith("+") || clean.startsWith("-") || clean.startsWith("@")) {
            clean = "'$clean"
        }
        val trimmed = clean.trim()
        return if (trimmed.isEmpty()) "unnamed_file" else trimmed
    }
}
