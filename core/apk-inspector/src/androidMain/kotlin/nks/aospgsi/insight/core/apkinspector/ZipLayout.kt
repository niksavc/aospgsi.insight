package nks.aospgsi.insight.core.apkinspector

import java.io.File
import java.io.RandomAccessFile

/** Where an entry's data starts inside the zip file, and how it is stored. */
internal data class ZipEntryLayout(val method: Int, val dataOffset: Long)

/**
 * Reads the zip central directory to find the file offset of each entry's data, which java.util.zip
 * does not expose. Needed to check 16 KiB alignment of stored native libraries.
 */
internal object ZipLayout {
  private const val EOCD_SIGNATURE = 0x06054b50L
  private const val CENTRAL_SIGNATURE = 0x02014b50L
  private const val MAX_EOCD_SEARCH = 22 + 65_535

  fun read(file: File, include: (String) -> Boolean): Map<String, ZipEntryLayout> =
    RandomAccessFile(file, "r").use { raf ->
      val length = raf.length()
      val tailSize = minOf(length, MAX_EOCD_SEARCH.toLong()).toInt()
      if (tailSize < 22) return emptyMap()
      val tail = ByteArray(tailSize)
      raf.seek(length - tailSize)
      raf.readFully(tail)

      var eocd = -1
      for (i in tailSize - 22 downTo 0) {
        if (u32(tail, i) == EOCD_SIGNATURE) {
          eocd = i
          break
        }
      }
      if (eocd < 0) return emptyMap()

      val entryCount = u16(tail, eocd + 10)
      val directorySize = u32(tail, eocd + 12)
      val directoryOffset = u32(tail, eocd + 16)
      if (directorySize > Int.MAX_VALUE || directoryOffset + directorySize > length)
        return emptyMap()
      val directory = ByteArray(directorySize.toInt())
      raf.seek(directoryOffset)
      raf.readFully(directory)

      val result = LinkedHashMap<String, ZipEntryLayout>()
      var position = 0
      for (i in 0 until entryCount) {
        if (position + 46 > directory.size || u32(directory, position) != CENTRAL_SIGNATURE) break
        val method = u16(directory, position + 10)
        val nameLength = u16(directory, position + 28)
        val extraLength = u16(directory, position + 30)
        val commentLength = u16(directory, position + 32)
        val localHeaderOffset = u32(directory, position + 42)
        if (position + 46 + nameLength > directory.size) break
        val name = String(directory, position + 46, nameLength, Charsets.UTF_8)
        if (include(name)) {
          val local = ByteArray(30)
          raf.seek(localHeaderOffset)
          raf.readFully(local)
          val localNameLength = u16(local, 26)
          val localExtraLength = u16(local, 28)
          result[name] =
            ZipEntryLayout(method, localHeaderOffset + 30 + localNameLength + localExtraLength)
        }
        position += 46 + nameLength + extraLength + commentLength
      }
      result
    }

  private fun u16(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

  private fun u32(bytes: ByteArray, offset: Int): Long =
    (u16(bytes, offset).toLong()) or (u16(bytes, offset + 2).toLong() shl 16)
}
