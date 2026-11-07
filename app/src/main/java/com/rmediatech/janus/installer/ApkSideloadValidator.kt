package com.rmediatech.janus.installer

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object ApkSideloadValidator {

    // Zip Magic Bytes: 'P' 'K' 0x03 0x04
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    /** Hard ceiling on ZIP central directory entries to defeat APK Zip-Bomb DoS (`SOVEREIGN-APK-ZIPSLIP-01`). */
    const val MAX_APK_ZIP_ENTRIES = 16_384

    /**
     * Validates whether [file] is a structurally sound and traversal-safe APK file.
     * Checks magic bytes, zip header, presence of both AndroidManifest.xml and classes.dex,
     * bounded entry count, and zero Zip-Slip directory traversal entries (`..`, `/`, `\`, null bytes).
     */
    fun validateApkFile(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 100) {
            return false
        }

        // 1. Check Magic Bytes
        val header = ByteArray(4)
        try {
            FileInputStream(file).use { input ->
                if (input.read(header) != 4) return false
            }
        } catch (_: Exception) {
            return false
        }

        if (!header.contentEquals(ZIP_MAGIC)) {
            return false
        }

        // 2. Verify AndroidManifest.xml & classes.dex inside zip entries while enforcing Zip-Slip & entry ceilings
        var hasManifest = false
        var hasDex = false
        var entryCount = 0
        try {
            ZipInputStream(FileInputStream(file)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    entryCount++
                    if (entryCount > MAX_APK_ZIP_ENTRIES) {
                        return false
                    }
                    val entryName = entry.name
                    if (isUnsafeZipEntryName(entryName)) {
                        return false
                    }
                    if (entryName == "AndroidManifest.xml") {
                        hasManifest = true
                    } else if (entryName == "classes.dex") {
                        hasDex = true
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        } catch (_: Exception) {
            return false
        }

        return hasManifest && hasDex
    }

    /**
     * Rejects Zip-Slip traversal paths (`../`, `..\\`, leading `/` or `\`, or null bytes).
     */
    fun isUnsafeZipEntryName(name: String): Boolean {
        if (name.isEmpty() || name.length > 1024) return true
        if (name.indexOf('\u0000') >= 0) return true
        if (name.startsWith("/") || name.startsWith("\\")) return true
        val normalized = name.replace('\\', '/')
        val segments = normalized.split('/')
        return segments.any { it == ".." }
    }

    /**
     * Computes streaming SHA-256 hex digest of [file] for LocalSend/KDE-Connect parity & sideload verification.
     */
    fun computeSha256Hex(file: File): String? {
        if (!file.exists() || !file.isFile) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            FileInputStream(file).use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } > 0) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Creates an Intent to prompt the user to install [apkFile].
     */
    fun createInstallIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
