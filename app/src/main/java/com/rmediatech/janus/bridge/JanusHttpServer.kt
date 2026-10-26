package com.rmediatech.janus.bridge

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.rmediatech.janus.clipboard.ClipboardItem
import com.rmediatech.janus.clipboard.JanusClipboardBridge
import com.rmediatech.janus.explorer.FileEntry
import com.rmediatech.janus.explorer.JanusFileSystem
import com.rmediatech.janus.installer.ApkSideloadValidator
import com.rmediatech.janus.logging.SovereignLogger
import com.rmediatech.janus.security.JanusAuthToken
import com.rmediatech.janus.security.PathTraversalShield
import fi.iki.elonen.NanoWSD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.Locale

/**
 * Sovereign Janus HTTP & WebSocket Bridge Server:
 * One Job: Dispatches local REST API endpoints (auth, scoped filesystem, clipboard, APK installer)
 * and delegates WebSocket lifecycle & ghost-socket self-healing to JanusWebSocketManager.
 */
class JanusHttpServer(
    val context: Context,
    val port: Int = 8080,
    val authToken: JanusAuthToken = JanusAuthToken(),
    val fileSystem: JanusFileSystem = JanusFileSystem(Environment.getExternalStorageDirectory())
) : NanoWSD(port) {

    var onClientCountChanged: ((Int) -> Unit)? = null
    var onActivityEvent: ((String) -> Unit)? = null

    private val wsManager = JanusWebSocketManager(
        onClientCountChanged = { count -> onClientCountChanged?.invoke(count) },
        onActivityEvent = { event -> onActivityEvent?.invoke(event) }
    )

    private val clipBroadcastListener: (ClipboardItem) -> Unit = { item ->
        wsManager.broadcast(JSONObject().put("type", "clipboard_update").put("item", item.toJson()).toString())
    }

    init {
        JanusClipboardBridge.addListener(clipBroadcastListener)
    }

    override fun stop() {
        JanusClipboardBridge.removeListener(clipBroadcastListener)
        wsManager.closeAll()
        super.stop()
    }

    fun pruneAndProbeConnections(): Int = wsManager.pruneAndProbeConnections()

    override fun serveHttp(session: IHTTPSession): Response {
        val startNs = System.nanoTime()
        val uri = session.uri
        val method = session.method
        val clientIp = session.remoteIpAddress ?: "unknown"

        if (method == Method.OPTIONS) return jsonResponse(Response.Status.OK, JSONObject())
        if (uri == "/" || uri == "/index.html") {
            val res = serveAsset("web/index.html", "text/html")
            val latencyMs = (System.nanoTime() - startNs) / 1_000_000.0
            SovereignLogger.i(SovereignLogger.Category.HTTP, "$method $uri from $clientIp (${String.format(Locale.US, "%.1f", latencyMs)}ms) -> 200 OK")
            return res
        }
        if (uri == "/api/v1/auth/login" && method == Method.POST) return handleLogin(session, clientIp)

        if (uri.startsWith("/api/")) {
            val token = session.headers["authorization"]?.removePrefix("Bearer ")?.trim()
                ?: session.parameters["token"]?.firstOrNull()?.trim()
            val clientId = session.headers["x-janus-client-id"]?.trim()
                ?: session.parameters["clientId"]?.firstOrNull()?.trim()

            if (!authToken.authenticate(token, clientId = clientId, clientIp = clientIp)) {
                val latencyMs = (System.nanoTime() - startNs) / 1_000_000.0
                SovereignLogger.w(SovereignLogger.Category.SECURITY, "Unauthorized API access: $method $uri from $clientIp (${String.format(Locale.US, "%.1f", latencyMs)}ms)")
                return jsonResponse(Response.Status.UNAUTHORIZED, JSONObject().put("error", "Unauthorized or session expired"))
            }
        }

        val res = try {
            when {
                uri == "/api/v1/auth/status" -> handleStatus(clientIp)
                uri == "/api/v1/logs" && method == Method.GET -> handleLogsGet(session, clientIp)
                uri == "/api/v1/fs/list" && method == Method.GET -> handleFsList(session, clientIp)
                uri == "/api/v1/fs/download" && method == Method.GET -> handleFsDownload(session, clientIp)
                uri == "/api/v1/fs/zip" && method == Method.GET -> handleFsZip(session, clientIp)
                uri == "/api/v1/fs/upload" && method == Method.POST -> handleFsUpload(session, clientIp)
                uri == "/api/v1/fs/mkdir" && method == Method.POST -> handleFsMkdir(session, clientIp)
                uri == "/api/v1/fs/delete" && method == Method.POST -> handleFsDelete(session, clientIp)
                uri == "/api/v1/clipboard" && method == Method.GET -> handleClipboardGet(clientIp)
                uri == "/api/v1/clipboard" && method == Method.POST -> handleClipboardPost(session, clientIp)
                uri == "/api/v1/install" && method == Method.POST -> handleApkInstall(session, clientIp)
                else -> jsonResponse(Response.Status.NOT_FOUND, JSONObject().put("error", "Endpoint not found: $uri"))
            }
        } catch (e: Exception) {
            SovereignLogger.e(SovereignLogger.Category.BRIDGE, "HTTP handler error on $uri from $clientIp", e)
            jsonResponse(Response.Status.INTERNAL_ERROR, JSONObject().put("error", e.message ?: "Internal error"))
        }

        val latencyMs = (System.nanoTime() - startNs) / 1_000_000.0
        SovereignLogger.i(SovereignLogger.Category.HTTP, "$method $uri from $clientIp (${String.format(Locale.US, "%.1f", latencyMs)}ms) -> ${res.status.description}")
        return res
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket = wsManager.createWebSocket(handshake)

    private fun handleStatus(clientIp: String): Response {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val totalBytes = stat.totalBytes
        val availableBytes = stat.availableBytes
        val json = JSONObject()
            .put("authenticated", true)
            .put("deviceName", Build.MODEL)
            .put("deviceBrand", Build.BRAND)
            .put("androidVersion", Build.VERSION.RELEASE)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("storageTotal", totalBytes)
            .put("storageAvailable", availableBytes)
            .put("storageFormatted", "${FileEntry.formatByteCount(availableBytes)} free of ${FileEntry.formatByteCount(totalBytes)}")
        SovereignLogger.i(SovereignLogger.Category.HTTP, "STATUS probe by $clientIp (${Build.MODEL}, Android ${Build.VERSION.RELEASE})")
        return jsonResponse(Response.Status.OK, json)
    }

    private fun handleLogsGet(session: IHTTPSession, clientIp: String): Response {
        val full = session.parameters["full"]?.firstOrNull()?.toBoolean() ?: false
        val limit = session.parameters["limit"]?.firstOrNull()?.toIntOrNull() ?: 200
        val json = JSONObject()
        if (full) {
            json.put("fullLog", SovereignLogger.getFullLogContent())
        } else {
            val lines = JSONArray()
            SovereignLogger.getRecentLogs(limit).forEach { lines.put(it) }
            json.put("lines", lines)
            json.put("count", lines.length())
        }
        SovereignLogger.i(SovereignLogger.Category.HTTP, "LOGS queried by $clientIp (full=$full, limit=$limit)")
        return jsonResponse(Response.Status.OK, json)
    }

    private fun handleLogin(session: IHTTPSession, clientIp: String): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val postData = files["postData"] ?: ""
        var pinOrSecret = ""
        var clientId = session.headers["x-janus-client-id"] ?: ""
        try {
            val json = JSONObject(postData)
            pinOrSecret = json.optString("pin", json.optString("token", ""))
            if (clientId.isBlank()) clientId = json.optString("clientId", "")
        } catch (_: Exception) {
            pinOrSecret = session.parameters["pin"]?.firstOrNull() ?: ""
        }

        return if (authToken.authenticate(pinOrSecret, clientId = clientId.ifBlank { "ws_$clientIp" }, clientIp = clientIp)) {
            SovereignLogger.i(SovereignLogger.Category.SECURITY, "Workstation paired/authenticated from $clientIp")
            jsonResponse(Response.Status.OK, JSONObject().put("success", true).put("token", authToken.sessionSecret))
        } else {
            SovereignLogger.w(SovereignLogger.Category.SECURITY, "Failed pairing attempt from $clientIp (locked=${authToken.isLocked()})")
            val err = if (authToken.isLocked()) "Too many failed attempts. Locked." else "Invalid credentials."
            jsonResponse(Response.Status.FORBIDDEN, JSONObject().put("success", false).put("error", err))
        }
    }

    private fun handleFsList(session: IHTTPSession, clientIp: String): Response {
        val path = session.parameters["path"]?.firstOrNull() ?: ""
        val entries = fileSystem.listDirectory(path)
        if (entries == null) {
            SovereignLogger.w(SovereignLogger.Category.FS, "LIST dir='$path' by $clientIp -> BLOCKED or NOT FOUND")
            return jsonResponse(Response.Status.BAD_REQUEST, JSONObject().put("error", "Directory not found or traversal blocked"))
        }

        val array = JSONArray()
        entries.forEach { array.put(it.toJson()) }
        SovereignLogger.i(SovereignLogger.Category.FS, "LIST dir='${path.ifBlank { "root" }}' by $clientIp -> ${entries.size} entries")
        return jsonResponse(Response.Status.OK, JSONObject().put("currentPath", path).put("entries", array))
    }

    private fun handleFsDownload(session: IHTTPSession, clientIp: String): Response {
        val path = session.parameters["path"]?.firstOrNull() ?: ""
        val file = fileSystem.getFile(path)
        if (file == null) {
            SovereignLogger.w(SovereignLogger.Category.FS, "DOWNLOAD file='$path' by $clientIp -> NOT FOUND")
            return jsonResponse(Response.Status.NOT_FOUND, JSONObject().put("error", "File not found"))
        }

        val safeName = PathTraversalShield.sanitizeFileName(file.name)
        SovereignLogger.i(SovereignLogger.Category.FS, "DOWNLOAD file='$safeName' (${file.length()} bytes) by $clientIp")
        return newFixedLengthResponse(Response.Status.OK, FileEntry.detectMimeType(safeName), FileInputStream(file), file.length()).apply {
            addHeader("Content-Disposition", "attachment; filename=\"$safeName\"")
        }
    }

    private fun handleFsZip(session: IHTTPSession, clientIp: String): Response {
        val path = session.parameters["path"]?.firstOrNull() ?: ""
        val pipeIn = PipedInputStream()
        val pipeOut = PipedOutputStream(pipeIn)
        Thread {
            try { fileSystem.streamZipArchive(path, pipeOut); pipeOut.close() }
            catch (_: Exception) { try { pipeOut.close() } catch (_: Exception) {} }
        }.start()

        val rawName = if (path.isBlank()) "janus_backup.zip" else "${path.substringAfterLast('/')}.zip"
        val downloadName = PathTraversalShield.sanitizeFileName(rawName)
        SovereignLogger.i(SovereignLogger.Category.FS, "ZIP folder='${path.ifBlank { "root" }}' (as '$downloadName') by $clientIp")
        return newChunkedResponse(Response.Status.OK, "application/zip", pipeIn).apply {
            addHeader("Content-Disposition", "attachment; filename=\"$downloadName\"")
        }
    }

    private fun handleFsUpload(session: IHTTPSession, clientIp: String): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val targetPath = session.parameters["path"]?.firstOrNull() ?: ""
        val uploadedTemp = files["file"]
            ?: return jsonResponse(Response.Status.BAD_REQUEST, JSONObject().put("error", "No file uploaded"))

        val safeName = PathTraversalShield.sanitizeFileName(session.parameters["fileName"]?.firstOrNull() ?: "upload.bin")
        val targetDir = PathTraversalShield.resolveSafePath(fileSystem.rootDir, targetPath)
            ?: return jsonResponse(Response.Status.BAD_REQUEST, JSONObject().put("error", "Invalid destination path"))

        val destFile = File(targetDir, safeName)
        File(uploadedTemp).copyTo(destFile, overwrite = true)
        onActivityEvent?.invoke("FILE_UPLOAD")
        SovereignLogger.i(SovereignLogger.Category.FS, "UPLOAD file='$safeName' (${destFile.length()} bytes) to '$targetPath' by $clientIp")
        return jsonResponse(Response.Status.OK, JSONObject().put("success", true).put("fileName", safeName))
    }

    private fun handleFsMkdir(session: IHTTPSession, clientIp: String): Response {
        session.parseBody(HashMap())
        val parent = session.parameters["parent"]?.firstOrNull() ?: ""
        val name = session.parameters["name"]?.firstOrNull() ?: ""
        val success = fileSystem.createDirectory(parent, name)
        SovereignLogger.i(SovereignLogger.Category.FS, "MKDIR parent='$parent' name='$name' by $clientIp (success=$success)")
        return jsonResponse(Response.Status.OK, JSONObject().put("success", success))
    }

    private fun handleFsDelete(session: IHTTPSession, clientIp: String): Response {
        session.parseBody(HashMap())
        val path = session.parameters["path"]?.firstOrNull() ?: ""
        val success = fileSystem.delete(path)
        SovereignLogger.i(SovereignLogger.Category.FS, "DELETE path='$path' by $clientIp (success=$success)")
        return jsonResponse(Response.Status.OK, JSONObject().put("success", success))
    }

    private fun handleClipboardGet(clientIp: String): Response {
        val history = JSONArray()
        JanusClipboardBridge.getHistory().forEach { history.put(it.toJson()) }
        val res = JSONObject()
            .put("latest", JanusClipboardBridge.getLatest()?.toJson() ?: JSONObject.NULL)
            .put("history", history)
        SovereignLogger.d(SovereignLogger.Category.CLIPBOARD, "CLIPBOARD poll by $clientIp (${history.length()} items)")
        return jsonResponse(Response.Status.OK, res)
    }

    private fun handleClipboardPost(session: IHTTPSession, clientIp: String): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val postData = files["postData"] ?: ""
        val text = try { JSONObject(postData).optString("text", "") }
        catch (_: Exception) { session.parameters["text"]?.firstOrNull() ?: "" }

        val item = JanusClipboardBridge.push(text, "desktop")
        return if (item != null) {
            onActivityEvent?.invoke("CLIPBOARD_SYNC")
            val preview = if (text.length > 35) "${text.take(35)}..." else text
            SovereignLogger.i(SovereignLogger.Category.CLIPBOARD, "CLIPBOARD push from desktop ($clientIp): '$preview' (${text.length} chars)")
            jsonResponse(Response.Status.OK, item.toJson())
        } else {
            SovereignLogger.w(SovereignLogger.Category.CLIPBOARD, "CLIPBOARD rejected empty push from $clientIp")
            jsonResponse(Response.Status.BAD_REQUEST, JSONObject().put("error", "Empty or invalid clipboard payload"))
        }
    }

    private fun handleApkInstall(session: IHTTPSession, clientIp: String): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val tempApkPath = files["apk"] ?: files["file"]
        val apkFile = if (tempApkPath != null) {
            File(tempApkPath)
        } else {
            val relPath = session.parameters["path"]?.firstOrNull() ?: ""
            if (relPath.isNotBlank()) fileSystem.getFile(relPath) else null
        } ?: return jsonResponse(Response.Status.BAD_REQUEST, JSONObject().put("error", "No APK file uploaded or specified"))

        if (!ApkSideloadValidator.validateApkFile(apkFile)) {
            SovereignLogger.w(SovereignLogger.Category.SECURITY, "Rejected invalid APK sideload archive by $clientIp")
            return jsonResponse(Response.Status.BAD_REQUEST, JSONObject().put("error", "Target file is not a valid APK archive"))
        }

        val destApk = File(context.cacheDir, "sideload_${System.currentTimeMillis()}.apk")
        apkFile.copyTo(destApk, overwrite = true)
        context.startActivity(ApkSideloadValidator.createInstallIntent(context, destApk))
        SovereignLogger.i(SovereignLogger.Category.BRIDGE, "Launched APK PackageInstaller for ${destApk.name} (${apkFile.length()} bytes) by $clientIp")
        return jsonResponse(Response.Status.OK, JSONObject().put("success", true).put("message", "Package installer launched on device"))
    }

    private fun serveAsset(assetPath: String, mimeType: String): Response = try {
        newChunkedResponse(Response.Status.OK, mimeType, context.assets.open(assetPath))
    } catch (_: Exception) {
        newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Asset not found: $assetPath")
    }

    private fun jsonResponse(status: Response.IStatus, json: JSONObject): Response =
        newFixedLengthResponse(status, "application/json", json.toString()).apply {
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Janus-Client-Id")
        }
}
