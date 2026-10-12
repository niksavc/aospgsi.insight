package nks.aospgsi.insight.core.apkinspector

import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ZipLayoutTest {
  @Test
  fun dataOffsetPointsAtStoredEntryContent() {
    val content = ByteArray(100) { (it + 1).toByte() }
    val file = File.createTempFile("ziplayout-", ".zip")
    try {
      ZipOutputStream(FileOutputStream(file)).use { zip ->
        val entry = ZipEntry("lib/arm64-v8a/libx.so")
        entry.method = ZipEntry.STORED
        entry.size = content.size.toLong()
        entry.compressedSize = content.size.toLong()
        entry.crc = CRC32().also { it.update(content) }.value
        zip.putNextEntry(entry)
        zip.write(content)
        zip.closeEntry()
      }

      val layout = ZipLayout.read(file) { true }
      val info = assertNotNull(layout["lib/arm64-v8a/libx.so"])
      assertEquals(ZipEntry.STORED, info.method)
      val start = info.dataOffset.toInt()
      assertContentEquals(content, file.readBytes().copyOfRange(start, start + content.size))
    } finally {
      file.delete()
    }
  }
}
