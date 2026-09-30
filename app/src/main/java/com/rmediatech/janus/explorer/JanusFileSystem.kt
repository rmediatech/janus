package com.rmediatech.janus.explorer

import com.rmediatech.janus.security.PathTraversalShield
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class JanusFileSystem(val rootDir: File) {

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
     * Zero-buffering: Writes chunks on the fly.
     */
    fun streamZipArchive(relativeDir: String, outStream: OutputStream) {
        val target = PathTraversalShield.resolveSafePath(rootDir, relativeDir) ?: return
        if (!target.exists()) return

        val zipOut = ZipOutputStream(outStream)
        val buffer = ByteArray(8192)

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
                target.walkTopDown().forEach { file ->
                    val entryName = file.relativeTo(target).path.replace('\\', '/')
                    if (entryName.isNotEmpty()) {
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
