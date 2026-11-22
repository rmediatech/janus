package com.rmediatech.sovereign.security

/**
 * Sovereign Universal Sanitizer (AGY-RULE-SOVEREIGN-SECURITY-01 Invariant III)
 * One Job: Scrubs hostile ANSI escape sequences, control bytes, and spreadsheet formula injection.
 * Single source of truth across all mobile and edge clients.
 */
object SovereignSanitizer {
    private val ANSI_CSI_REGEX = Regex("\u001B\\[[;\\d]*[ -/]*[@-~]")
    private val ANSI_OSC_REGEX = Regex("\u001B\\][^\u0007]*\u0007")

    /**
     * Neutralizes spreadsheet formula injection (=, +, -, @, tab, CR) by prefixing a single-quote,
     * while stripping out raw ANSI terminal control bytes and malicious formatting.
     */
    fun sanitize(raw: String?, maxLen: Int = 4096): String {
        if (raw.isNullOrBlank()) return ""
        val withoutOsc = ANSI_OSC_REGEX.replace(raw, "")
        val withoutAnsi = ANSI_CSI_REGEX.replace(withoutOsc, "")
        val cleaned = withoutAnsi.filter { ch -> !ch.isISOControl() || ch == '\n' || ch == '\t' || ch == '\r' }
            .take(maxLen)
            .trim()

        if (cleaned.isEmpty()) return ""
        val first = cleaned.first()
        return if (first == '=' || first == '+' || first == '-' || first == '@' || raw.startsWith("\t") || raw.startsWith("\r")) {
            "'$cleaned"
        } else {
            cleaned
        }
    }
}
