package com.rmediatech.janus.bridge

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.rmediatech.janus.clipboard.ClipboardItem
import com.rmediatech.janus.clipboard.JanusClipboardBridge
import com.rmediatech.janus.devtools.JanusLogcatStreamer
import com.rmediatech.janus.explorer.FileEntry
import com.rmediatech.janus.explorer.JanusFileSystem
import com.rmediatech.janus.installer.ApkSideloadValidator
import com.rmediatech.janus.security.JanusAuthToken
import com.rmediatech.janus.security.PathTraversalShield
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList

class JanusHttpServer(
    val context: Context,
    val port: Int = 8080,
    val authToken: JanusAuthToken = JanusAuthToken(),
    val fileSystem: JanusFileSystem = JanusFileSystem(Environment.getExternalStorageDirectory())
) : NanoWSD(port) {

    private val connectedWebSockets = CopyOnWriteArrayList<JanusWebSocket>()
    var onClientCountChanged: ((Int) -> Unit)? = null
    var onActivityEvent: ((String) -> Unit)? = null

    init {
        // Broadcast phone clipboard events to all connected desktop WebSockets
        JanusClipboardBridge.addListener { item ->
            val payload = JSONObject().apply {
                put("type", "clipboard_update")
                put("item", item.toJson())
            }.toString()
            broadcastWebSocket(payload)
        }
    }

    override fun serveHttp(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        // 1. Static Web Workstation SPA
        if (uri == "/" || uri == "/index.html") {
            return serveAsset("web/index.html", "text/html")
        }

        // 2. Authentication status & Login endpoints (public for pairing handshake)
        if (uri == "/api/v1/auth/login" && method == Method.POST) {
            return handleLogin(session)
        }

        // Check authentication for all /api/ endpoints
        if (uri.startsWith("/api/")) {
            val token = session.headers["authorization"]?.removePrefix("Bearer ")?.trim()
                ?: session.parameters["token"]?.firstOrNull()?.trim()

            if (!authToken.authenticate(token)) {
                return jsonResponse(Response.Status.UNAUTHORIZED, JSONObject().put("error", "Unauthorized or session expired"))
            }
        }

        return try {
            when {
                uri == "/api/v1/auth/status" -> handleStatus()
                uri == "/api/v1/fs/list" && method == Method.GET -> handleFsList(session)
                uri == "/api/v1/fs/download" && method == Method.GET -> handleFsDownload(session)
                uri == "/api/v1/fs/zip" && method == Method.GET -> handleFsZip(session)
                uri == "/api/v1/fs/upload" && method == Method.POST -> handleFsUpload(session)
                uri == "/api/v1/fs/mkdir" && method == Method.POST -> handleFsMkdir(session)
                uri == "/api/v1/fs/delete" && method == Method.POST -> handleFsDelete(session)
                uri == "/api/v1/clipboard" && method == Method.GET -> handleClipboardGet()
                uri == "/api/v1/clipboard" && method == Method.POST -> handleClipboardPost(session)
                uri == "/api/v1/install" && method == Method.POST -> handleApkInstall(session)
                else -> jsonResponse(Response.Status.NOT_FOUND, JSONObject().put("error", "Endpoint not found: $uri"))
            }
        } catch (e: Exception) {
            jsonResponse(Response.Status.INTERNAL_ERROR, JSONObject().put("error", e.message ?: "Internal error"))
        }
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        return JanusWebSocket(handshake)
    }

    private fun handleStatus(): Response {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val totalBytes = stat.totalBytes
        val availableBytes = stat.availableBytes

        val json = JSONObject().apply {
            put("authenticated", true)
            put("deviceName", Build.MODEL)
            put("deviceBrand", Build.BRAND)
            put("androidVersion", Build.VERSION.RELEASE)
            put("sdkInt", Build.VERSION.SDK_INT)
            put("storageTotal", totalBytes)
            put("storageAvailable", availableBytes)
            put("storageFormatted", "${FileEntry.formatByteCount(availableBytes)} free of ${FileEntry.formatByteCount(totalBytes)}")
        }
        return jsonResponse(Response.Status.OK, json)
    }

    private fun handleLogin(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val postData = files["postData"] ?: ""
        var pinOrSecret = ""
        try {
            val json = JSONObject(postData)
            pinOrSecret = json.optString("pin", json.optString("token", ""))
        } catch (_: Exception) {
            pinOrSecret = session.parameters["pin"]?.firstOrNull() ?: ""
        }

        return if (authToken.authenticate(pinOrSecret)) {
            val res = JSONObject().apply {
                put("success", true)
                put("token", authToken.sessionSecret)
            }
            jsonResponse(Response.Status.OK, res)
        } else {
            val res = JSONObject().apply {
                put("success", false)
                put("error", if (authToken.isLocked()) "Too many failed attempts. Locked." else "Invalid credentials.")
            }
            jsonResponse(Response.Status.FORBIDDEN, res)
        }
    }

    private fun handleFsList(session: IHTTPSession): Response {
        val path = session.parameters["path"]?.firstOrNull() ?: ""
        val entries = fileSystem.listDirectory(path)
            ?: return jsonResponse(Response.Status.BAD_REQUEST, JSONObject().put("error", "Directory not found or traversal blocked"))

        val array = JSONArray()
        entries.forEach { array.put(it.toJson()) }

        val result = JSONObject().apply {
            put("currentPath", path)
            put("entries", array)
        }
        return jsonResponse(Response.Status.OK, result)
    }

    private fun handleFsDownload(session: IHTTPSession): Response {
        val path = session.parameters["path"]?.firstOrNull() ?: ""
        val file = fileSystem.getFile(path)
            ?: return jsonResponse(Response.Status.NOT_FOUND, JSONObject().put("error", "File not found"))

        val mimeType = FileEntry.detectMimeType(file.name)
        val fis = FileInputStream(file)
        val res = newFixedLengthResponse(Response.Status.OK, mimeType, fis, file.length())
        res.addHeader("Content-Disposition", "attachment; filename=\"${file.name}\"")
        return res
    }

    private fun handleFsZip(session: IHTTPSession): Response {
        val path = session.parameters["path"]?.firstOrNull() ?: ""
        val pipeIn = PipedInputStream()
        val pipeOut = PipedOutputStream(pipeIn)

        Thread {
            try {
                fileSystem.streamZipArchive(path, pipeOut)
                pipeOut.close()
            } catch (_: Exception) {
                try { pipeOut.close() } catch (_: Exception) {}
            }
        }.start()

        val downloadName = if (path.isBlank()) "janus_backup.zip" else "${path.substringAfterLast('/')}.zip"
        val res = newChunkedResponse(Response.Status.OK, "application/zip", pipeIn)
        res.addHeader("Content-Disposition", "attachment; filename=\"$downloadName\"")
        return res
    }

    private fun handleFsUpload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val targetPath = session.parameters["path"]?.firstOrNull() ?: ""

        val uploadedTemp = files["file"]
        val fileName = session.parameters["fileName"]?.firstOrNull() ?: "upload.bin"
        val safeName = PathTraversalShield.sanitizeFileName(fileName)

        if (uploadedTemp == null) {
            return jsonResponse(Response.Status.BAD_REQUEST, JSONObject().put("error", "No file uploaded"))
        }

        val targetDir = PathTraversalShield.resolveSafePath(fileSystem.rootDir, targetPath)
            ?: return jsonResponse(Response.Status.BAD_REQUEST, JSONObject().put("error", "Invalid destination path"))

        val destFile = File(targetDir, safeName)
        File(uploadedTemp).copyTo(destFile, overwrite = true)
        onActivityEvent?.invoke("FILE_UPLOAD")

        return jsonResponse(Response.Status.OK, JSONObject().put("success", true).put("fileName", safeName))
    }

    private fun handleFsMkdir(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val parent = session.parameters["parent"]?.firstOrNull() ?: ""
        val name = session.parameters["name"]?.firstOrNull() ?: ""

        val success = fileSystem.createDirectory(parent, name)
        return jsonResponse(Response.Status.OK, JSONObject().put("success", success))
    }

    private fun handleFsDelete(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val path = session.parameters["path"]?.firstOrNull() ?: ""

        val success = fileSystem.delete(path)
        return jsonResponse(Response.Status.OK, JSONObject().put("success", success))
    }

    private fun handleClipboardGet(): Response {
        val history = JSONArray()
        JanusClipboardBridge.getHistory().forEach { history.put(it.toJson()) }

        val res = JSONObject().apply {
            put("latest", JanusClipboardBridge.getLatest()?.toJson() ?: JSONObject.NULL)
            put("history", history)
        }
        return jsonResponse(Response.Status.OK, res)
    }

    private fun handleClipboardPost(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val postData = files["postData"] ?: ""
        var text = ""
        try {
            val json = JSONObject(postData)
            text = json.optString("text", "")
        } catch (_: Exception) {
            text = session.parameters["text"]?.firstOrNull() ?: ""
        }

        val item = JanusClipboardBridge.push(text, "desktop")
        return if (item != null) {
            onActivityEvent?.invoke("CLIPBOARD_SYNC")
            jsonResponse(Response.Status.OK, item.toJson())
        } else {
            jsonResponse(Response.Status.BAD_REQUEST, JSONObject().put("error", "Empty or invalid clipboard payload"))
        }
    }

    private fun handleApkInstall(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val tempApkPath = files["apk"] ?: files["file"]
            ?: return jsonResponse(Response.Status.BAD_REQUEST, JSONObject().put("error", "No APK file uploaded"))

        val tempFile = File(tempApkPath)
        if (!ApkSideloadValidator.validateApkFile(tempFile)) {
            return jsonResponse(Response.Status.BAD_REQUEST, JSONObject().put("error", "Uploaded file is not a valid APK archive"))
        }

        // Move to app cache directory
        val destApk = File(context.cacheDir, "sideload_${System.currentTimeMillis()}.apk")
        tempFile.copyTo(destApk, overwrite = true)

        // Launch PackageInstaller
        val intent = ApkSideloadValidator.createInstallIntent(context, destApk)
        context.startActivity(intent)

        return jsonResponse(Response.Status.OK, JSONObject().put("success", true).put("message", "Package installer launched on device"))
    }

    private fun serveAsset(assetPath: String, mimeType: String): Response {
        return try {
            val stream = context.assets.open(assetPath)
            newChunkedResponse(Response.Status.OK, mimeType, stream)
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Asset not found: $assetPath")
        }
    }

    private fun jsonResponse(status: Response.IStatus, json: JSONObject): Response {
        return newFixedLengthResponse(status, "application/json", json.toString()).apply {
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        }
    }

    private fun broadcastWebSocket(message: String) {
        for (ws in connectedWebSockets) {
            try {
                if (ws.isOpen) {
                    ws.send(message)
                    ws.flushSocket()
                }
            } catch (_: Exception) {}
        }
    }

    inner class JanusWebSocket(handshake: IHTTPSession) : WebSocket(handshake) {
        private val outField by lazy {
            try {
                val f = WebSocket::class.java.getDeclaredField("out")
                f.isAccessible = true
                f
            } catch (_: Exception) { null }
        }

        fun flushSocket() {
            try {
                (outField?.get(this) as? java.io.OutputStream)?.flush()
            } catch (_: Exception) {}
        }

        private var isSubscribedToLogcat = false
        private val logcatListener: (String) -> Unit = { line ->
            try {
                val msg = JSONObject().apply {
                    put("type", "logcat_line")
                    put("line", line)
                }.toString()
                send(msg)
                flushSocket()
            } catch (_: Exception) {}
        }

        override fun onOpen() {
            connectedWebSockets.add(this)
            onClientCountChanged?.invoke(connectedWebSockets.size)
            onActivityEvent?.invoke("CLIENT_CONNECTED")
            val latest = JanusClipboardBridge.getLatest()
            if (latest != null) {
                try {
                    val initMsg = JSONObject().apply {
                        put("type", "clipboard_update")
                        put("item", latest.toJson())
                    }.toString()
                    send(initMsg)
                    flushSocket()
                } catch (_: Exception) {}
            }
        }

        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            connectedWebSockets.remove(this)
            onClientCountChanged?.invoke(connectedWebSockets.size)
            onActivityEvent?.invoke("CLIENT_DISCONNECTED")
            if (isSubscribedToLogcat) {
                JanusLogcatStreamer.removeListener(logcatListener)
            }
        }

        override fun onMessage(message: WebSocketFrame) {
            try {
                val json = JSONObject(message.textPayload)
                when (json.optString("action")) {
                    "start_logcat" -> {
                        if (!isSubscribedToLogcat) {
                            isSubscribedToLogcat = true
                            JanusLogcatStreamer.addListener(logcatListener)
                        }
                    }
                    "stop_logcat" -> {
                        if (isSubscribedToLogcat) {
                            isSubscribedToLogcat = false
                            JanusLogcatStreamer.removeListener(logcatListener)
                        }
                    }
                    "clipboard_push" -> {
                        val text = json.optString("text")
                        if (text.isNotBlank()) {
                            JanusClipboardBridge.push(text, "desktop")
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        override fun onPong(pong: WebSocketFrame?) {}
        override fun onException(exception: java.io.IOException?) {}
    }
}
