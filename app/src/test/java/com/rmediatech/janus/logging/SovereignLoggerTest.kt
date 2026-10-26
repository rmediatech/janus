package com.rmediatech.janus.logging

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SovereignLoggerTest {

    @Before
    fun setup() {
        SovereignLogger.clearLogs()
    }

    @Test
    fun testRingBufferAccumulationAndBound() {
        for (i in 1..1100) {
            SovereignLogger.i(SovereignLogger.Category.BRIDGE, "Bridge event $i")
        }

        val logs = SovereignLogger.getRecentLogs(1000)
        assertTrue("Ring buffer must hold entries", logs.isNotEmpty())
        assertEquals("Ring buffer must cap at 1000 entries", 1000, logs.size)
        assertTrue("Latest entry must be present", logs.last().contains("Bridge event 1100"))
        assertFalse("Evicted entry must not be present", logs.any { it.contains("Bridge event 1 ") })
    }

    @Test
    fun testAnsiAndNewlineScrubbingInLogs() {
        val poisoned = "Alert\u001b[31;1m RED\u001b[0m\nSecond Line\rThird Line"
        SovereignLogger.w(SovereignLogger.Category.SECURITY, poisoned)

        val lastLog = SovereignLogger.getRecentLogs(1).first()
        assertFalse("ANSI escape sequence must be scrubbed", lastLog.contains("\u001b["))
        assertFalse("Newlines must be collapsed into spaces", lastLog.contains("\n"))
        assertFalse("Carriage returns must be collapsed into spaces", lastLog.contains("\r"))
        assertTrue("Category header must be present", lastLog.contains("[SECURITY]"))
    }

    @Test
    fun testCompartmentalizedCategories() {
        SovereignLogger.i(SovereignLogger.Category.LIFECYCLE, "onTemporalResume triggered")
        SovereignLogger.i(SovereignLogger.Category.NETWORK, "LAN IP drift healed")
        SovereignLogger.i(SovereignLogger.Category.DISCOVERY, "mDNS registered")
        SovereignLogger.i(SovereignLogger.Category.WEBSOCKET, "Client connected")
        SovereignLogger.i(SovereignLogger.Category.HTTP, "GET /api/v1/fs/list from 10.0.0.1 (1.2ms)")
        SovereignLogger.i(SovereignLogger.Category.FS, "LIST dir='root' -> 22 entries")
        SovereignLogger.i(SovereignLogger.Category.CLIPBOARD, "SYNC from desktop: 'test' (4 chars)")

        val recent = SovereignLogger.getRecentLogs(10)
        assertTrue(recent.any { it.contains("[LIFECYCLE]") && it.contains("onTemporalResume") })
        assertTrue(recent.any { it.contains("[NETWORK]") && it.contains("LAN IP drift") })
        assertTrue(recent.any { it.contains("[DISCOVERY]") && it.contains("mDNS") })
        assertTrue(recent.any { it.contains("[WEBSOCKET]") && it.contains("Client connected") })
        assertTrue(recent.any { it.contains("[HTTP]") && it.contains("GET /api/v1/fs/list") })
        assertTrue(recent.any { it.contains("[FS]") && it.contains("LIST dir='root'") })
        assertTrue(recent.any { it.contains("[CLIPBOARD]") && it.contains("SYNC from desktop") })
    }
}
