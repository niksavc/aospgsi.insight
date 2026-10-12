package nks.aospgsi.insight.core.apkinspector

/**
 * What the inspector needs from an ELF shared object: its class and the alignment of PT_LOAD
 * segments.
 */
data class ElfInfo(val is64Bit: Boolean, val loadAlignments: List<Long>)

/** Minimal ELF program-header reader. Pure Kotlin so it can be unit-tested without Android. */
object ElfReader {
  const val SIXTEEN_KB = 16384L

  private const val PT_LOAD = 1L

  /**
   * Parses the ELF header and program headers from the start of a file, or null if not a valid ELF.
   */
  fun parse(bytes: ByteArray): ElfInfo? {
    if (bytes.size < 52) return null
    if (
      bytes[0] != 0x7F.toByte() ||
        bytes[1] != 'E'.code.toByte() ||
        bytes[2] != 'L'.code.toByte() ||
        bytes[3] != 'F'.code.toByte()
    ) {
      return null
    }
    val is64 =
      when (bytes[4].toInt()) {
        1 -> false
        2 -> true
        else -> return null
      }
    val little =
      when (bytes[5].toInt()) {
        1 -> true
        2 -> false
        else -> return null
      }

    fun u16(offset: Int): Int = readUnsigned(bytes, offset, 2, little).toInt()

    fun u32(offset: Int): Long = readUnsigned(bytes, offset, 4, little)

    fun u64(offset: Int): Long = readUnsigned(bytes, offset, 8, little)

    if (bytes.size < (if (is64) 64 else 52)) return null
    val phoff = if (is64) u64(0x20) else u32(0x1C)
    val phentsize = u16(if (is64) 0x36 else 0x2A)
    val phnum = u16(if (is64) 0x38 else 0x2C)
    if (phoff < 0 || phoff > Int.MAX_VALUE || phentsize < (if (is64) 56 else 32)) return null

    val alignments = mutableListOf<Long>()
    for (i in 0 until phnum) {
      val base = phoff.toInt() + i * phentsize
      if (base + phentsize > bytes.size) return null
      if (u32(base) == PT_LOAD) {
        alignments += if (is64) u64(base + 0x30) else u32(base + 0x1C)
      }
    }
    return ElfInfo(is64, alignments)
  }

  /** True when there is at least one PT_LOAD segment and every one is aligned to [boundary]. */
  fun isLoadAlignedTo(info: ElfInfo, boundary: Long = SIXTEEN_KB): Boolean =
    info.loadAlignments.isNotEmpty() &&
      info.loadAlignments.all { it >= boundary && it % boundary == 0L }

  private fun readUnsigned(bytes: ByteArray, offset: Int, size: Int, little: Boolean): Long {
    var value = 0L
    for (i in 0 until size) {
      val b = bytes[offset + if (little) i else size - 1 - i].toLong() and 0xFF
      value = value or (b shl (8 * i))
    }
    return value
  }
}
