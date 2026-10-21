package com.rmediatech.janus.security

/**
 * Sovereign Input & Payload Sanitizer for Janus:
 * Enforces strict mathematical invariants and OJP security boundaries.
 * Defends against:
 * 1. CSV / Formula injection (=, +, -, @, \t, \r)
 * 2. ANSI CSI / OSC terminal and log poison escape sequences
 * 3. Allocation bombs (unbounded clipboard, filename, path, or log strings)
 * 4. Malformed IPv4 addresses and TCP port numbers
 */
object SovereignSanitizer {

    const val MAX_CLIPBOARD_BYTES = 65_536
    const val MAX_FILENAME_LENGTH = 255
    const val MAX_PATH_LENGTH = 1024
    const val MAX_LOG_MESSAGE_LENGTH = 2048

    private val ANSI_ESCAPE_REGEX = Regex(
        """\u001B\[[0-?]*[ -/]*[@-~]|\u001B\][^\u0007\u001B]*(\u0007|\u001B\\)|\u001B[@-_]"""
    )
    private val CONTROL_CHAR_REGEX = Regex("""[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]""")
    private val ALL_CONTROL_AND_NULL_REGEX = Regex("""[\x00-\x1F\x7F]""")

    /**
     * Strips raw ANSI escape sequences and non-printable control characters from arbitrary strings.
     * Bounds output to [maxLength] to prevent allocation bombs.
     */
    fun sanitizeAnsi(input: String, maxLength: Int = MAX_LOG_MESSAGE_LENGTH): String {
        if (input.isEmpty()) return ""
        val capped = if (input.length > maxLength * 2) input.substring(0, maxLength * 2) else input
        val noAnsi = ANSI_ESCAPE_REGEX.replace(capped, "")
        val noCtrl = CONTROL_CHAR_REGEX.replace(noAnsi, "").trim()
        return if (noCtrl.length > maxLength) noCtrl.substring(0, maxLength) else noCtrl
    }

    /**
     * Sanitizes clipboard text: strips ANSI escape sequences and null bytes, and caps length
     * at [MAX_CLIPBOARD_BYTES] to prevent memory exhaustion bombs.
     */
    fun sanitizeClipboard(raw: String): String {
        if (raw.isEmpty()) return ""
        val capped = if (raw.length > MAX_CLIPBOARD_BYTES) raw.substring(0, MAX_CLIPBOARD_BYTES) else raw
        val noAnsi = ANSI_ESCAPE_REGEX.replace(capped, "")
        return noAnsi.replace("\u0000", "")
    }

    /**
     * Neutralizes spreadsheet / CSV formula injection prefixes (=, +, -, @, \t, \r)
     * after scrubbing ANSI escapes and control characters.
     */
    fun sanitizeFormulaField(input: String?, maxLength: Int = MAX_FILENAME_LENGTH): String {
        if (input.isNullOrBlank()) return ""
        val capped = if (input.length > maxLength * 2) input.substring(0, maxLength * 2) else input
        val noAnsi = ANSI_ESCAPE_REGEX.replace(capped, "")
        val hadLeadingTabOrCr = noAnsi.startsWith('\t') || noAnsi.startsWith('\r')
        var clean = ALL_CONTROL_AND_NULL_REGEX.replace(noAnsi, "").trim()
        if (clean.isEmpty()) return ""

        val first = clean[0]
        if (hadLeadingTabOrCr || first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            if (!clean.startsWith("'")) {
                clean = "'$clean"
            }
        }
        return if (clean.length > maxLength) clean.substring(0, maxLength) else clean
    }

    /**
     * Strictly validates whether a string is a well-formed IPv4 address (0.0.0.0 to 255.255.255.255)
     * with zero octal prefix ambiguity.
     */
    fun isValidIpv4(ip: String): Boolean {
        val parts = ip.trim().split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            if (part.isEmpty() || (part.length > 1 && part.startsWith('0'))) return false
            val num = part.toIntOrNull() ?: return false
            num in 0..255
        }
    }

    /**
     * Validates whether a TCP port number is within 1..65535.
     */
    fun isValidPort(port: Int): Boolean = port in 1..65535
}
