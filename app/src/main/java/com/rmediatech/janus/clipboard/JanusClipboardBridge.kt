package com.rmediatech.janus.clipboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

data class ClipboardItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val source: String, // "phone" or "desktop"
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("text", text)
        obj.put("source", source)
        obj.put("timestamp", timestamp)
        return obj
    }
}

/**
 * JanusClipboardBridge manages bi-directional clipboard sync with ANSI scrubbing
 * and terminal injection protection.
 */
object JanusClipboardBridge {

    private const val MAX_HISTORY = 30
    private val history = mutableListOf<ClipboardItem>()
    private val listeners = CopyOnWriteArrayList<(ClipboardItem) -> Unit>()
    private val _latestState = MutableStateFlow<ClipboardItem?>(null)
    val latestState = _latestState.asStateFlow()

    // ANSI escape code stripper (CSI, OSC, and raw escapes)
    private val ANSI_REGEX = Regex("(\u001B\\[[0-?]*[ -/]*[@-~]|\u001B\\][^\u0007]*\u0007|\u001B[@-_])")

    /**
     * Sanitizes raw text: removes ANSI sequences, null bytes, and dangerous terminal escapes.
     */
    fun sanitize(raw: String): String {
        var clean = ANSI_REGEX.replace(raw, "")
        clean = clean.replace("\u0000", "")
        return clean
    }

    /**
     * Pushes new clipboard text from either "phone" or "desktop".
     * Broadcasts to active listeners if the content is new.
     */
    @Synchronized
    fun push(text: String, source: String): ClipboardItem? {
        val clean = sanitize(text)
        if (clean.isBlank()) return null

        // Deduplicate consecutive identical items
        if (history.isNotEmpty() && history.first().text == clean) {
            return history.first()
        }

        val item = ClipboardItem(text = clean, source = source)
        history.add(0, item)
        if (history.size > MAX_HISTORY) {
            history.removeAt(history.size - 1)
        }
        _latestState.value = item

        // Notify subscribers
        for (listener in listeners) {
            try {
                listener(item)
            } catch (_: Exception) {}
        }

        return item
    }

    @Synchronized
    fun getLatest(): ClipboardItem? = history.firstOrNull()

    @Synchronized
    fun getHistory(): List<ClipboardItem> = history.toList()

    @Synchronized
    fun clearHistory() {
        history.clear()
        _latestState.value = null
    }

    fun addListener(listener: (ClipboardItem) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (ClipboardItem) -> Unit) {
        listeners.remove(listener)
    }
}
