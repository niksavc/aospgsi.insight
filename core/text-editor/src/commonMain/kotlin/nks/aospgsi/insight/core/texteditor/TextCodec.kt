package nks.aospgsi.insight.core.texteditor

/** How a file separates lines. The editor works with `\n` and writes the original style back. */
enum class LineEnding(val sequence: String) {
  LF("\n"),
  CRLF("\r\n"),
  CR("\r"),
}

/**
 * A text file as the editor holds it: [text] uses `\n` for every line break, and the original line
 * ending style and byte order mark are kept so that saving does not rewrite them.
 */
class TextDocument(
  val text: String,
  val lineEnding: LineEnding,
  val hasBom: Boolean,
  /** True when the file mixed line ending styles; saving makes them all [lineEnding]. */
  val mixedLineEndings: Boolean,
)

/** Result of reading bytes as a text file. */
sealed interface TextLoad {
  class Ok(val document: TextDocument) : TextLoad

  /** Contains NUL bytes: a binary file, or text in an encoding other than UTF-8. */
  data object Binary : TextLoad

  /** Not valid UTF-8, so saving it back would change bytes the editor cannot show. */
  data object NotUtf8 : TextLoad

  data class TooLarge(val limitBytes: Int) : TextLoad
}

/** Reading and writing UTF-8 text files without damaging what the editor cannot represent. */
object TextCodec {
  /** Bigger files are refused: the text field gets slow, and a binary file is likelier. */
  const val MAX_BYTES: Int = 4 * 1024 * 1024

  private const val BINARY_SCAN_BYTES = 8000

  fun decode(bytes: ByteArray): TextLoad {
    if (bytes.size > MAX_BYTES) return TextLoad.TooLarge(MAX_BYTES)
    val hasBom =
      bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() &&
        bytes[1] == 0xBB.toByte() &&
        bytes[2] == 0xBF.toByte()
    val start = if (hasBom) 3 else 0
    for (i in start until minOf(bytes.size, BINARY_SCAN_BYTES)) {
      if (bytes[i] == 0.toByte()) return TextLoad.Binary
    }
    val raw =
      try {
        bytes.decodeToString(start, bytes.size, throwOnInvalidSequence = true)
      } catch (_: Exception) {
        return TextLoad.NotUtf8
      }
    val counts = countLineBreaks(raw)
    val styles = listOf(counts.crlf, counts.lf, counts.cr).count { it > 0 }
    val ending =
      when {
        counts.crlf >= counts.lf && counts.crlf >= counts.cr && counts.crlf > 0 -> LineEnding.CRLF
        counts.cr > counts.lf && counts.cr > counts.crlf -> LineEnding.CR
        else -> LineEnding.LF
      }
    return TextLoad.Ok(TextDocument(normalize(raw), ending, hasBom, mixedLineEndings = styles > 1))
  }

  fun encode(text: String, lineEnding: LineEnding, hasBom: Boolean): ByteArray {
    val body =
      normalize(text).let {
        if (lineEnding == LineEnding.LF) it else it.replace("\n", lineEnding.sequence)
      }
    val encoded = body.encodeToByteArray()
    if (!hasBom) return encoded
    return byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + encoded
  }

  /** Turns `\r\n` and lone `\r` into `\n`. */
  fun normalize(text: String): String =
    if ('\r' !in text) text else text.replace("\r\n", "\n").replace('\r', '\n')

  private class Counts(val crlf: Int, val lf: Int, val cr: Int)

  private fun countLineBreaks(text: String): Counts {
    var crlf = 0
    var lf = 0
    var cr = 0
    var i = 0
    while (i < text.length) {
      when (text[i]) {
        '\r' ->
          if (i + 1 < text.length && text[i + 1] == '\n') {
            crlf++
            i++
          } else {
            cr++
          }
        '\n' -> lf++
      }
      i++
    }
    return Counts(crlf, lf, cr)
  }
}
