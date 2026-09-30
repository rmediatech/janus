package com.rmediatech.janus.clipboard

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class JanusClipboardBridgeTest {

    @Before
    fun setup() {
        JanusClipboardBridge.clearHistory()
    }

    @Test
    fun testPushAndDeduplicate() {
        val item1 = JanusClipboardBridge.push("git status", "desktop")
        assertNotNull(item1)
        assertEquals("git status", item1!!.text)
        assertEquals("desktop", item1.source)

        // Duplicate push should not add redundant entry
        val item2 = JanusClipboardBridge.push("git status", "desktop")
        assertEquals(item1.id, item2!!.id)
        assertEquals(1, JanusClipboardBridge.getHistory().size)

        // New item pushes to top
        val item3 = JanusClipboardBridge.push("https://github.com", "phone")
        assertNotNull(item3)
        assertEquals(2, JanusClipboardBridge.getHistory().size)
        assertEquals("https://github.com", JanusClipboardBridge.getLatest()!!.text)
    }

    @Test
    fun testAnsiAndTerminalInjectionScrubbing() {
        val coloredPayload = "\u001B[31;1mRed Alert\u001B[0m"
        assertEquals("Red Alert", JanusClipboardBridge.sanitize(coloredPayload))

        val oscTitlePoison = "\u001B]0;Injected Title\u0007Hello World"
        assertEquals("Hello World", JanusClipboardBridge.sanitize(oscTitlePoison))

        val withNulls = "secret\u0000token"
        assertEquals("secrettoken", JanusClipboardBridge.sanitize(withNulls))
    }

    @Test
    fun testListenerNotification() {
        var notifiedText: String? = null
        val listener: (ClipboardItem) -> Unit = { item ->
            notifiedText = item.text
        }

        JanusClipboardBridge.addListener(listener)
        JanusClipboardBridge.push("export API_KEY=12345", "desktop")
        assertEquals("export API_KEY=12345", notifiedText)

        JanusClipboardBridge.removeListener(listener)
        JanusClipboardBridge.push("next item", "phone")
        assertEquals("export API_KEY=12345", notifiedText) // Unsubscribed, unchanged
    }

    @Test
    fun testHistoryRingBufferCap() {
        for (i in 1..40) {
            JanusClipboardBridge.push("item-$i", "phone")
        }
        val history = JanusClipboardBridge.getHistory()
        assertEquals(30, history.size)
        assertEquals("item-40", history.first().text)
    }
}
