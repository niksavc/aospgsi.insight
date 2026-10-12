package nks.aospgsi.insight.core.apktool

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Test helper: where each entry's data starts in a zip, read from the central directory. */
internal object ZipOffsets {
  data class Entry(val name: String, val method: Int, val dataOffset: Long)

  fun read(file: File): List<Entry> {
    RandomAccessFile(file, "r").use { raf ->
      val bytes = ByteArray(raf.length().toInt())
      raf.readFully(bytes)
      val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
      var eocd = bytes.size - 22
      while (eocd >= 0 && buf.getInt(eocd) != 0x06054b50) eocd--
      check(eocd >= 0) { "No end of central directory" }
      val count = buf.getShort(eocd + 10).toInt() and 0xFFFF
      var pos = buf.getInt(eocd + 16)
      val entries = mutableListOf<Entry>()
      repeat(count) {
        check(buf.getInt(pos) == 0x02014b50)
        val method = buf.getShort(pos + 10).toInt() and 0xFFFF
        val nameLen = buf.getShort(pos + 28).toInt() and 0xFFFF
        val extraLen = buf.getShort(pos + 30).toInt() and 0xFFFF
        val commentLen = buf.getShort(pos + 32).toInt() and 0xFFFF
        val localOffset = buf.getInt(pos + 42)
        val name = String(bytes, pos + 46, nameLen, Charsets.UTF_8)
        val localNameLen = buf.getShort(localOffset + 26).toInt() and 0xFFFF
        val localExtraLen = buf.getShort(localOffset + 28).toInt() and 0xFFFF
        entries += Entry(name, method, (localOffset + 30 + localNameLen + localExtraLen).toLong())
        pos += 46 + nameLen + extraLen + commentLen
      }
      return entries
    }
  }
}
