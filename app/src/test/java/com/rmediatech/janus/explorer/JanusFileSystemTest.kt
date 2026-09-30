package com.rmediatech.janus.explorer

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

class JanusFileSystemTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var fs: JanusFileSystem

    @Before
    fun setup() {
        rootDir = tempFolder.newFolder("storage_root")
        fs = JanusFileSystem(rootDir)

        // Seed folder structure
        File(rootDir, "docs").mkdir()
        File(rootDir, "docs/notes.txt").writeText("Sample note content")
        File(rootDir, "docs/nested").mkdir()
        File(rootDir, "docs/nested/deep.json").writeText("{\"key\":\"value\"}")
        File(rootDir, "music").mkdir()
        File(rootDir, "music/song.mp3").writeBytes(ByteArray(1024))
        File(rootDir, "app.apk").writeBytes(ByteArray(2048))
    }

    @Test
    fun testListRootDirectory() {
        val entries = fs.listDirectory("")
        assertNotNull(entries)
        assertEquals(3, entries!!.size) // app.apk, docs, music

        // Directories must come first
        assertTrue(entries[0].isDirectory)
        assertTrue(entries[1].isDirectory)
        assertFalse(entries[2].isDirectory)

        // Verify apk detection
        val apkEntry = entries.find { it.name == "app.apk" }
        assertNotNull(apkEntry)
        assertTrue(apkEntry!!.isApk)
        assertEquals("application/vnd.android.package-archive", apkEntry.mimeType)
    }

    @Test
    fun testListSubDirectory() {
        val entries = fs.listDirectory("docs")
        assertNotNull(entries)
        assertEquals(2, entries!!.size) // nested (dir), notes.txt (file)
        assertEquals("nested", entries[0].name)
        assertTrue(entries[0].isDirectory)
        assertEquals("notes.txt", entries[1].name)
        assertFalse(entries[1].isDirectory)
    }

    @Test
    fun testTraversalBlockOnList() {
        assertNull(fs.listDirectory("../"))
        assertNull(fs.listDirectory("../../etc"))
    }

    @Test
    fun testCreateAndDeleteDirectory() {
        assertTrue(fs.createDirectory("", "new_folder"))
        val created = File(rootDir, "new_folder")
        assertTrue(created.exists())
        assertTrue(created.isDirectory)

        // Delete
        assertTrue(fs.delete("new_folder"))
        assertFalse(created.exists())

        // Refuse to delete root
        assertFalse(fs.delete(""))
        assertFalse(fs.delete("/"))
        assertFalse(fs.delete("."))
    }

    @Test
    fun testStreamZipArchive() {
        val byteOut = ByteArrayOutputStream()
        fs.streamZipArchive("docs", byteOut)
        val zipBytes = byteOut.toByteArray()
        assertTrue(zipBytes.isNotEmpty())

        // Inspect zip archive entries
        val zipIn = ZipInputStream(ByteArrayInputStream(zipBytes))
        val entryNames = mutableListOf<String>()
        var entry = zipIn.nextEntry
        while (entry != null) {
            entryNames.add(entry.name)
            zipIn.closeEntry()
            entry = zipIn.nextEntry
        }

        assertTrue(entryNames.contains("notes.txt"))
        assertTrue(entryNames.contains("nested/") || entryNames.contains("nested/deep.json"))
    }

    @Test
    fun testFormatByteCount() {
        assertEquals("0 B", FileEntry.formatByteCount(0))
        assertEquals("500.0 B", FileEntry.formatByteCount(500))
        assertEquals("1.0 KB", FileEntry.formatByteCount(1024))
        assertEquals("1.5 MB", FileEntry.formatByteCount((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.0 GB", FileEntry.formatByteCount((2L * 1024 * 1024 * 1024)))
    }
}
