package com.rmediatech.janus.installer

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkSideloadValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testValidApkPasses() {
        val apkFile = tempFolder.newFile("valid.apk")
        ZipOutputStream(FileOutputStream(apkFile)).use { zipOut ->
            zipOut.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zipOut.write("dummy manifest content".toByteArray())
            zipOut.closeEntry()

            zipOut.putNextEntry(ZipEntry("classes.dex"))
            zipOut.write(ByteArray(500))
            zipOut.closeEntry()
        }

        assertTrue(ApkSideloadValidator.validateApkFile(apkFile))
    }

    @Test
    fun testZipWithoutManifestFails() {
        val nonApkZip = tempFolder.newFile("regular.zip")
        ZipOutputStream(FileOutputStream(nonApkZip)).use { zipOut ->
            zipOut.putNextEntry(ZipEntry("document.txt"))
            zipOut.write("hello".toByteArray())
            zipOut.closeEntry()
        }

        assertFalse(ApkSideloadValidator.validateApkFile(nonApkZip))
    }

    @Test
    fun testCorruptedOrEmptyFileFails() {
        val emptyFile = tempFolder.newFile("empty.apk")
        assertFalse(ApkSideloadValidator.validateApkFile(emptyFile))

        val garbageFile = tempFolder.newFile("garbage.apk")
        garbageFile.writeBytes(byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55))
        assertFalse(ApkSideloadValidator.validateApkFile(garbageFile))
    }
}
