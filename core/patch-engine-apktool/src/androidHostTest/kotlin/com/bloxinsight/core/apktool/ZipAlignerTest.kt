package com.bloxinsight.core.apktool

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZipAlignerTest {
  private fun tempDir(): File = kotlin.io.path.createTempDirectory("zip-aligner").toFile()

  private fun ZipOutputStream.stored(name: String, data: ByteArray) {
    val entry = ZipEntry(name)
    entry.method = ZipEntry.STORED
    entry.size = data.size.toLong()
    entry.compressedSize = data.size.toLong()
    entry.crc = CRC32().also { it.update(data) }.value
    putNextEntry(entry)
    write(data)
    closeEntry()
  }

  private fun ZipOutputStream.deflated(name: String, data: ByteArray) {
    putNextEntry(ZipEntry(name))
    write(data)
    closeEntry()
  }

  @Test
  fun paddingReachesTheAlignmentAndSkipsWhenAlreadyAligned() {
    assertNull(ZipAligner.padding(headerEnd = 8, alignment = 4))
    for (end in 0L..40L) {
      val extra = ZipAligner.padding(end, 4)
      assertEquals(0L, (end + (extra?.size ?: 0)) % 4, "headerEnd=$end")
    }
    val big = ZipAligner.padding(headerEnd = 70, alignment = 16384)
    assertNotNull(big)
    assertEquals(0L, (70 + big.size) % 16384)
  }

  @Test
  fun storedEntriesAreAlignedAndContentsSurvive() {
    val dir = tempDir()
    val input = File(dir, "in.zip")
    val arsc = ByteArray(1001) { it.toByte() }
    val lib64 = ByteArray(5003) { (it * 3).toByte() }
    val lib32 = ByteArray(77) { 9 }
    val manifest = "<manifest/>".repeat(50).toByteArray()
    ZipOutputStream(input.outputStream()).use {
      it.deflated("AndroidManifest.xml", manifest)
      it.stored("resources.arsc", arsc)
      it.stored("lib/arm64-v8a/libx.so", lib64)
      it.stored("lib/armeabi-v7a/liby.so", lib32)
      it.deflated("assets/a.txt", "hello".toByteArray())
    }

    val output = File(dir, "out.zip")
    ZipAligner.align(input, output)

    val offsets = ZipOffsets.read(output).associateBy { it.name }
    assertEquals(0L, offsets.getValue("resources.arsc").dataOffset % 4)
    assertEquals(0L, offsets.getValue("lib/arm64-v8a/libx.so").dataOffset % 16384)
    assertEquals(0L, offsets.getValue("lib/armeabi-v7a/liby.so").dataOffset % 16384)
    assertEquals(ZipEntry.DEFLATED, offsets.getValue("AndroidManifest.xml").method)

    ZipFile(output).use { zip ->
      fun bytes(name: String) = zip.getInputStream(zip.getEntry(name)).use { it.readBytes() }
      assertContentEquals(arsc, bytes("resources.arsc"))
      assertContentEquals(lib64, bytes("lib/arm64-v8a/libx.so"))
      assertContentEquals(lib32, bytes("lib/armeabi-v7a/liby.so"))
      assertContentEquals(manifest, bytes("AndroidManifest.xml"))
      assertEquals("hello", String(bytes("assets/a.txt")))
      assertTrue(zip.size() == 5)
    }
    dir.deleteRecursively()
  }
}
