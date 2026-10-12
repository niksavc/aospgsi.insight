package com.bloxinsight.core.apktool

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Rewrites a zip so that every stored (uncompressed) entry starts at an aligned offset: 4 bytes in
 * general, 16 KiB for native libraries so they can be mapped directly on 16 KiB page-size devices.
 * Apktool's rebuild deflates everything, so resources.arsc (which Android 11+ requires stored and
 * 4-byte aligned for apps targeting API 30+) and native libraries are stored here; other entries
 * keep the method they have. Must run before signing, because it changes the file layout.
 */
internal object ZipAligner {
  const val DEFAULT_ALIGNMENT = 4
  const val NATIVE_LIB_ALIGNMENT = 16 * 1024

  /** Fixed, in-range timestamp (2009-01-01 UTC) so the output does not depend on input times. */
  private const val FIXED_TIME = 1_230_768_000_000L

  private const val LOCAL_HEADER_SIZE = 30
  private const val ALIGNMENT_EXTRA_ID = 0xD935

  fun align(input: File, output: File) {
    ZipFile(input).use { zip ->
      val counting = CountingOutputStream(BufferedOutputStream(FileOutputStream(output), 1 shl 16))
      ZipOutputStream(counting).use { out ->
        for (entry in zip.entries()) {
          val copy = ZipEntry(entry.name)
          copy.time = FIXED_TIME
          if (!entry.isDirectory && (entry.method == ZipEntry.STORED || mustStore(entry.name))) {
            copy.method = ZipEntry.STORED
            copy.size = entry.size
            copy.compressedSize = entry.size
            copy.crc = entry.crc
            val nameLength = entry.name.toByteArray(Charsets.UTF_8).size
            copy.extra =
              padding(counting.count + LOCAL_HEADER_SIZE + nameLength, alignmentFor(entry.name))
          } else {
            copy.method = ZipEntry.DEFLATED
          }
          out.putNextEntry(copy)
          if (!entry.isDirectory) zip.getInputStream(entry).use { it.copyTo(out) }
          out.closeEntry()
        }
      }
    }
  }

  private fun mustStore(name: String): Boolean = name == "resources.arsc" || isNativeLib(name)

  private fun isNativeLib(name: String): Boolean = name.startsWith("lib/") && name.endsWith(".so")

  fun alignmentFor(name: String): Int =
    if (isNativeLib(name)) NATIVE_LIB_ALIGNMENT else DEFAULT_ALIGNMENT

  /**
   * Builds the zip "extra" bytes that push the entry data (which follows the header at [headerEnd])
   * onto a multiple of [alignment]; null when no padding is needed.
   */
  fun padding(headerEnd: Long, alignment: Int): ByteArray? {
    var pad = ((alignment - headerEnd % alignment) % alignment).toInt()
    if (pad == 0) return null
    // An extra field needs at least 4 bytes (id + size); skip a whole alignment unit if short.
    if (pad < 4) pad += alignment
    val extra = ByteArray(pad)
    extra[0] = (ALIGNMENT_EXTRA_ID and 0xFF).toByte()
    extra[1] = (ALIGNMENT_EXTRA_ID shr 8).toByte()
    extra[2] = ((pad - 4) and 0xFF).toByte()
    extra[3] = ((pad - 4) shr 8).toByte()
    return extra
  }

  private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
    var count = 0L
      private set

    override fun write(b: Int) {
      out.write(b)
      count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
      out.write(b, off, len)
      count += len
    }
  }
}
