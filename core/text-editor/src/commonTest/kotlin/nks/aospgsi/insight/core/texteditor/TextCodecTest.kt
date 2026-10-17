package nks.aospgsi.insight.core.texteditor

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TextCodecTest {
  private fun load(bytes: ByteArray): TextDocument =
    assertIs<TextLoad.Ok>(TextCodec.decode(bytes)).document

  @Test
  fun plainUtf8UsesLfAndHasNoBom() {
    val doc = load("héllo\nwörld\n".encodeToByteArray())
    assertEquals("héllo\nwörld\n", doc.text)
    assertEquals(LineEnding.LF, doc.lineEnding)
    assertFalse(doc.hasBom)
    assertFalse(doc.mixedLineEndings)
  }

  @Test
  fun windowsLineEndingsAreNormalizedAndWrittenBack() {
    val original = "one\r\ntwo\r\nthree".encodeToByteArray()
    val doc = load(original)
    assertEquals("one\ntwo\nthree", doc.text)
    assertEquals(LineEnding.CRLF, doc.lineEnding)
    assertContentEquals(original, TextCodec.encode(doc.text, doc.lineEnding, doc.hasBom))
  }

  @Test
  fun oldMacLineEndingsRoundTrip() {
    val original = "a\rb\rc".encodeToByteArray()
    val doc = load(original)
    assertEquals(LineEnding.CR, doc.lineEnding)
    assertEquals("a\nb\nc", doc.text)
    assertContentEquals(original, TextCodec.encode(doc.text, doc.lineEnding, doc.hasBom))
  }

  @Test
  fun byteOrderMarkIsHiddenFromTheTextButKept() {
    val original =
      byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "text".encodeToByteArray()
    val doc = load(original)
    assertTrue(doc.hasBom)
    assertEquals("text", doc.text)
    assertContentEquals(original, TextCodec.encode(doc.text, doc.lineEnding, doc.hasBom))
  }

  @Test
  fun mixedLineEndingsAreFlaggedAndTheCommonOneWins() {
    val doc = load("a\r\nb\r\nc\nd".encodeToByteArray())
    assertTrue(doc.mixedLineEndings)
    assertEquals(LineEnding.CRLF, doc.lineEnding)
    assertEquals("a\nb\nc\nd", doc.text)
  }

  @Test
  fun emptyFileIsFine() {
    val doc = load(ByteArray(0))
    assertEquals("", doc.text)
    assertEquals(LineEnding.LF, doc.lineEnding)
  }

  @Test
  fun nulBytesMeanBinary() {
    assertEquals(TextLoad.Binary, TextCodec.decode(byteArrayOf(0x50, 0x4B, 0, 0, 1, 2)))
  }

  @Test
  fun invalidUtf8IsRefusedInsteadOfBeingDamaged() {
    // 0xE9 alone is Latin-1 "é"; as UTF-8 it is a truncated sequence.
    assertEquals(
      TextLoad.NotUtf8,
      TextCodec.decode(byteArrayOf('a'.code.toByte(), 0xE9.toByte(), 'b'.code.toByte())),
    )
  }

  @Test
  fun oversizedFilesAreRefused() {
    val big = ByteArray(TextCodec.MAX_BYTES + 1) { 'a'.code.toByte() }
    assertEquals(TextLoad.TooLarge(TextCodec.MAX_BYTES), TextCodec.decode(big))
  }

  @Test
  fun textPastedWithCarriageReturnsIsStillWrittenWithOneStyle() {
    val bytes = TextCodec.encode("a\r\nb\rc\nd", LineEnding.CRLF, hasBom = false)
    assertEquals("a\r\nb\r\nc\r\nd", bytes.decodeToString())
  }
}
