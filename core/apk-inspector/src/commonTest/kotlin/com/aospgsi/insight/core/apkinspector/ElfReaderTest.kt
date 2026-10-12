package com.aospgsi.insight.core.apkinspector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ElfReaderTest {
  private fun put(bytes: ByteArray, offset: Int, value: Long, size: Int) {
    for (i in 0 until size) bytes[offset + i] = ((value shr (8 * i)) and 0xFF).toByte()
  }

  /** Builds a minimal little-endian 64-bit ELF with one PT_LOAD program header per alignment. */
  private fun elf64(vararg alignments: Long): ByteArray {
    val phentsize = 56
    val bytes = ByteArray(64 + phentsize * alignments.size)
    bytes[0] = 0x7F
    bytes[1] = 'E'.code.toByte()
    bytes[2] = 'L'.code.toByte()
    bytes[3] = 'F'.code.toByte()
    bytes[4] = 2
    bytes[5] = 1
    put(bytes, 0x20, 64, 8)
    put(bytes, 0x36, phentsize.toLong(), 2)
    put(bytes, 0x38, alignments.size.toLong(), 2)
    alignments.forEachIndexed { i, align ->
      val base = 64 + i * phentsize
      put(bytes, base, 1, 4)
      put(bytes, base + 0x30, align, 8)
    }
    return bytes
  }

  @Test
  fun sixteenKbAlignedSegmentsAreRecognized() {
    val info = assertNotNull(ElfReader.parse(elf64(0x4000, 0x4000)))
    assertTrue(info.is64Bit)
    assertEquals(listOf(0x4000L, 0x4000L), info.loadAlignments)
    assertTrue(ElfReader.isLoadAlignedTo(info))
  }

  @Test
  fun fourKbAlignedSegmentsAreNotSixteenKbReady() {
    val info = assertNotNull(ElfReader.parse(elf64(0x4000, 0x1000)))
    assertFalse(ElfReader.isLoadAlignedTo(info))
  }

  @Test
  fun nonElfAndTruncatedInputsReturnNull() {
    assertNull(ElfReader.parse(ByteArray(10)))
    assertNull(ElfReader.parse(ByteArray(200)))
    assertNull(ElfReader.parse(elf64(0x4000).copyOf(70)))
  }

  @Test
  fun sixteenKbWarningsCountOnlySixtyFourBitLibraries() {
    fun lib(abi: String, elf: Boolean?, zip: Boolean?) =
      com.aospgsi.insight.core.model.NativeLibraryInfo(
        path = "lib/$abi/libx.so",
        abi = abi,
        is64Bit = InspectionRules.is64BitAbi(abi),
        storedUncompressed = zip != null,
        zipAlignedTo16Kb = zip,
        elfAlignedTo16Kb = elf,
      )
    assertTrue(
      InspectionRules.sixteenKbWarnings(listOf(lib("armeabi-v7a", false, false))).isEmpty(),
    )
    assertTrue(InspectionRules.sixteenKbWarnings(listOf(lib("arm64-v8a", true, true))).isEmpty())
    val warnings =
      InspectionRules.sixteenKbWarnings(
        listOf(lib("arm64-v8a", false, false), lib("x86_64", true, null)),
      )
    assertEquals(2, warnings.size)
  }
}
