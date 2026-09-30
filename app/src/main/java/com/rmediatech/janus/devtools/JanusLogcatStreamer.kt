package com.rmediatech.janus.devtools

import com.rmediatech.janus.clipboard.JanusClipboardBridge
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList

object JanusLogcatStreamer {

    private var process: Process? = null
    private var isStreaming = false
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()

    @Synchronized
    fun startStreaming() {
        if (isStreaming) return
        isStreaming = true

        Thread {
            try {
                // Clear old buffer and stream with standard time format
                val proc = ProcessBuilder("logcat", "-v", "time").start()
                process = proc
                val reader = BufferedReader(InputStreamReader(proc.inputStream))

                var line: String? = reader.readLine()
                while (isStreaming && line != null) {
                    val cleanLine = JanusClipboardBridge.sanitize(line)
                    for (listener in listeners) {
                        try {
                            listener(cleanLine)
                        } catch (_: Exception) {}
                    }
                    line = reader.readLine()
                }
            } catch (_: Exception) {
            } finally {
                stopStreaming()
            }
        }.apply {
            isDaemon = true
            name = "JanusLogcatThread"
            start()
        }
    }

    @Synchronized
    fun stopStreaming() {
        isStreaming = false
        try {
            process?.destroy()
        } catch (_: Exception) {}
        process = null
    }

    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
        if (!isStreaming) {
            startStreaming()
        }
    }

    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            stopStreaming()
        }
    }
}
