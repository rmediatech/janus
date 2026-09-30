package com.rmediatech.janus.installer

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

object ApkSideloadValidator {

    // Zip Magic Bytes: 'P' 'K' 0x03 0x04
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    /**
     * Validates whether [file] is a structurally sound APK file.
     * Checks magic bytes, zip header, and presence of AndroidManifest.xml.
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

        // 2. Verify AndroidManifest.xml inside zip entries
        var hasManifest = false
        try {
            ZipInputStream(FileInputStream(file)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == "AndroidManifest.xml") {
                        hasManifest = true
                        break
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        } catch (_: Exception) {
            return false
        }

        return hasManifest
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
