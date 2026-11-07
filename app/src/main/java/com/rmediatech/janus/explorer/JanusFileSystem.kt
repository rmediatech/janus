package com.rmediatech.janus.explorer

import com.rmediatech.janus.installer.ApkSideloadValidator
import com.rmediatech.janus.security.PathTraversalShield
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class JanusFileSystem(val rootDir: File) {

    companion object {
        /** Maximum entries emitted in a single streamed ZIP archive (`SOVEREIGN-ARCHIVE-BOUND-01`). */
        const val MAX_STREAM_ARCHIVE_ENTRIES = 10_000
    }

    /**
     * Lists directory entries for [relativeDir].
     * Returns null if traversal attempted or not a directory.
     */
    fun listDirectory(relativeDir: String): List<FileEntry>? {
        val target = PathTraversalShield.resolveSafePath(rootDir, relativeDir) ?: return null
        if (!target.exists() || !target.isDirectory) return null

        val files = target.listFiles() ?: return emptyList()

        return files.map { file ->
            val relPath = file.relativeTo(rootDir).path.replace('\\', '/')
            FileEntry(
                name = file.name,
                relativePath = relPath,
                isDirectory = file.isDirectory,
                size = if (file.isDirectory) 0L else file.length(),
                lastModified = file.lastModified(),
                mimeType = if (file.isDirectory) "inode/directory" else FileEntry.detectMimeType(file.name)
            )
        }.sortedWith(
            compareByDescending<FileEntry> { it.isDirectory }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
    }

    /**
     * Resolves a file for reading or downloading.
     */
    fun getFile(relativePath: String): File? {
        val target = PathTraversalShield.resolveSafePath(rootDir, relativePath) ?: return null
        return if (target.exists() && target.isFile) target else null
    }

    /**
     * Computes the streaming SHA-256 hex digest of a file inside the jail (`SOVEREIGN-FILE-HASH-01`).
     */
    fun computeFileSha256(relativePath: String): String? {
        val target = getFile(relativePath) ?: return null
        return ApkSideloadValidator.computeSha256Hex(target)
    }

    /**
     * Creates a new directory at [parentRelative]/[dirName].
     */
    fun createDirectory(parentRelative: String, dirName: String): Boolean {
        val safeName = PathTraversalShield.sanitizeFileName(dirName)
        val combined = if (parentRelative.isBlank()) safeName else "$parentRelative/$safeName"
        val target = PathTraversalShield.resolveSafePath(rootDir, combined) ?: return false
        return if (!target.exists()) target.mkdirs() else false
    }

    /**
     * Deletes a file or directory at [relativePath].
     */
    fun delete(relativePath: String): Boolean {
        if (relativePath.isBlank() || relativePath == "/" || relativePath == ".") return false
        val target = PathTraversalShield.resolveSafePath(rootDir, relativePath) ?: return false
        if (!target.exists() || target == rootDir) return false
        return target.deleteRecursively()
    }

    /**
     * Streams the contents of [relativeDir] as a Zip archive directly to [outStream].
     * Zero-buffering: Writes chunks on the fly while skipping symbolic links and bounding entry count.
     */
    fun streamZipArchive(relativeDir: String, outStream: OutputStream) {
        val target = PathTraversalShield.resolveSafePath(rootDir, relativeDir) ?: return
        if (!target.exists()) return

        val zipOut = ZipOutputStream(outStream)
        val buffer = ByteArray(8192)
        var emittedEntries = 0

        try {
            if (target.isFile) {
                zipOut.putNextEntry(ZipEntry(target.name))
                FileInputStream(target).use { input ->
                    var len: Int
                    while (input.read(buffer).also { len = it } > 0) {
                        zipOut.write(buffer, 0, len)
                    }
                }
                zipOut.closeEntry()
            } else {
                for (file in target.walkTopDown()) {
                    if (emittedEntries >= MAX_STREAM_ARCHIVE_ENTRIES) break
                    if (Files.isSymbolicLink(file.toPath())) continue
                    val entryName = file.relativeTo(target).path.replace('\\', '/')
                    if (entryName.isNotEmpty() && !ApkSideloadValidator.isUnsafeZipEntryName(entryName)) {
                        emittedEntries++
                        if (file.isDirectory) {
                            zipOut.putNextEntry(ZipEntry("$entryName/"))
                            zipOut.closeEntry()
                        } else {
                            zipOut.putNextEntry(ZipEntry(entryName))
                            FileInputStream(file).use { input ->
                                var len: Int
                                while (input.read(buffer).also { len = it } > 0) {
                                    zipOut.write(buffer, 0, len)
                                }
                            }
                            zipOut.closeEntry()
                        }
                    }
                }
            }
        } finally {
            zipOut.finish()
            zipOut.flush()
        }
    }
}
