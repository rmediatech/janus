package com.rmediatech.janus.logging

import android.content.Context
import android.util.Log
import com.rmediatech.janus.security.SovereignSanitizer
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Sovereign Compartmentalized & Tamper-Resistant Logger for Janus:
 * One Job: Structured, rolling diagnostic logging across all Janus lifecycles,
 * HTTP/WebSocket bridge events, discovery, and security defenses.
 * Outputs to Logcat, a 100-entry in-memory ring buffer, and a rolling 512KB local file
 * (~/files/logs/janus.log) with zero cloud telemetry.
 */
object SovereignLogger {

    enum class Category {
        LIFECYCLE,
        NETWORK,
        DISCOVERY,
        BRIDGE,
        HTTP,
        FS,
        CLIPBOARD,
        WEBSOCKET,
        SECURITY
    }

    private const val LOGCAT_TAG = "Janus"
    private const val MAX_LOG_SIZE_BYTES = 2 * 1024 * 1024L // 2 MB
    private const val RING_BUFFER_SIZE = 1000

    private val ringBuffer = ConcurrentLinkedDeque<String>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var logFile: File? = null

    fun init(context: Context) {
        try {
            val logDir = File(context.filesDir, "logs")
            if (!logDir.exists()) logDir.mkdirs()
            logFile = File(logDir, "janus.log")
            i(Category.LIFECYCLE, "SovereignLogger initialized. Target: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            try {
                Log.w(LOGCAT_TAG, "Failed initializing log file: ${e.message}")
            } catch (_: Throwable) {}
        }
    }

    fun d(category: Category, message: String) = log("DEBUG", category, message, null)
    fun i(category: Category, message: String) = log("INFO", category, message, null)
    fun w(category: Category, message: String, tr: Throwable? = null) = log("WARN", category, message, tr)
    fun e(category: Category, message: String, tr: Throwable? = null) = log("ERROR", category, message, tr)

    private fun log(level: String, category: Category, message: String, tr: Throwable?) {
        val cleanMsg = SovereignSanitizer.sanitizeAnsi(message).replace("\n", " ").replace("\r", " ")
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val errSuffix = if (tr != null) {
            val cleanErr = SovereignSanitizer.sanitizeAnsi(tr.message ?: "").replace("\n", " ")
            " [Ex: ${tr.javaClass.simpleName} - $cleanErr]"
        } else ""
        val formatted = "[$timestamp] [$level] [${category.name}] $cleanMsg$errSuffix"

        // 1. Android Logcat output (guarded for JVM unit tests)
        try {
            when (level) {
                "DEBUG" -> Log.d("$LOGCAT_TAG:${category.name}", cleanMsg, tr)
                "INFO" -> Log.i("$LOGCAT_TAG:${category.name}", cleanMsg, tr)
                "WARN" -> Log.w("$LOGCAT_TAG:${category.name}", cleanMsg, tr)
                "ERROR" -> Log.e("$LOGCAT_TAG:${category.name}", cleanMsg, tr)
            }
        } catch (_: Throwable) {}

        // 2. In-Memory Ring Buffer (bounded to 100 entries)
        ringBuffer.addLast(formatted)
        while (ringBuffer.size > RING_BUFFER_SIZE) {
            ringBuffer.pollFirst()
        }

        // 3. Persistent File Append with 512KB Rolling Guard
        val file = logFile ?: return
        try {
            synchronized(this) {
                if (file.exists() && file.length() > MAX_LOG_SIZE_BYTES) {
                    val rotated = File(file.parentFile, "janus.log.1")
                    if (rotated.exists()) rotated.delete()
                    file.renameTo(rotated)
                }
                FileWriter(file, true).use { writer ->
                    writer.write(formatted)
                    writer.write("\n")
                }
            }
        } catch (_: Exception) {
            // Non-fatal: Never crash app during logging
        }
    }

    fun getRecentLogs(limit: Int = 50): List<String> {
        val snapshot = ringBuffer.toList()
        return if (snapshot.size > limit) snapshot.takeLast(limit) else snapshot
    }

    fun getFullLogContent(): String {
        return try {
            val file = logFile
            if (file != null && file.exists()) {
                file.readText()
            } else {
                ringBuffer.joinToString("\n")
            }
        } catch (_: Exception) {
            ringBuffer.joinToString("\n")
        }
    }

    fun clearLogs() {
        ringBuffer.clear()
        try {
            val file = logFile
            if (file != null && file.exists()) {
                file.writeText("")
            }
        } catch (_: Exception) {}
    }

    fun getLogFilePath(): String? = logFile?.absolutePath
}
