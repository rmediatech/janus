package com.rmediatech.janus.explorer

import org.json.JSONObject

data class FileEntry(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val mimeType: String
) {
    val isApk: Boolean
        get() = name.endsWith(".apk", ignoreCase = true)

    val formattedSize: String
        get() = formatByteCount(size)

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("name", name)
        obj.put("path", relativePath)
        obj.put("isDirectory", isDirectory)
        obj.put("size", size)
        obj.put("formattedSize", formattedSize)
        obj.put("lastModified", lastModified)
        obj.put("mimeType", mimeType)
        obj.put("isApk", isApk)
        return obj
    }

    companion object {
        fun formatByteCount(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            val clampedGroup = digitGroups.coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, clampedGroup.toDouble())
            return "%.1f %s".format(value, units[clampedGroup])
        }

        fun detectMimeType(fileName: String): String {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "apk" -> "application/vnd.android.package-archive"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "svg" -> "image/svg+xml"
                "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "flac" -> "audio/flac"
                "pdf" -> "application/pdf"
                "json" -> "application/json"
                "xml" -> "application/xml"
                "txt", "log", "md", "properties", "env" -> "text/plain"
                "kt", "java", "rs", "c", "cpp", "py", "sh", "js", "html", "css" -> "text/plain"
                "zip" -> "application/zip"
                "tar", "gz" -> "application/gzip"
                else -> "application/octet-stream"
            }
        }
    }
}
