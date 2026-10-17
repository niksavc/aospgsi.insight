package nks.aospgsi.insight.core.texteditor

/** Search and replace over editor text. Matches never overlap. */
object TextSearch {
  /** Start offsets of every match of [query] in [text]; empty for an empty query. */
  fun findAll(text: String, query: String, ignoreCase: Boolean): List<Int> {
    if (query.isEmpty()) return emptyList()
    val found = mutableListOf<Int>()
    var from = 0
    while (true) {
      val at = text.indexOf(query, from, ignoreCase)
      if (at < 0) return found
      found += at
      from = at + query.length
    }
  }

  /** The first match starting at or after [from], wrapping to the top; null if there is none. */
  fun next(text: String, query: String, from: Int, ignoreCase: Boolean): Int? {
    if (query.isEmpty()) return null
    val start = from.coerceIn(0, text.length)
    val at = text.indexOf(query, start, ignoreCase)
    if (at >= 0) return at
    val wrapped = text.indexOf(query, 0, ignoreCase)
    return if (wrapped >= 0) wrapped else null
  }

  /** The last match starting before [before], wrapping to the bottom; null if there is none. */
  fun previous(text: String, query: String, before: Int, ignoreCase: Boolean): Int? {
    if (query.isEmpty()) return null
    val last = (before - 1).coerceAtMost(text.length - query.length)
    if (last >= 0) {
      val at = text.lastIndexOf(query, last, ignoreCase)
      if (at >= 0) return at
    }
    val wrapped = text.lastIndexOf(query, text.length - query.length, ignoreCase)
    return if (wrapped >= 0) wrapped else null
  }

  /** [text] with every match replaced, and how many there were. */
  fun replaceAll(
    text: String,
    query: String,
    replacement: String,
    ignoreCase: Boolean,
  ): Pair<String, Int> {
    val matches = findAll(text, query, ignoreCase)
    if (matches.isEmpty()) return text to 0
    val out = StringBuilder(text.length)
    var cursor = 0
    for (at in matches) {
      out.append(text, cursor, at).append(replacement)
      cursor = at + query.length
    }
    out.append(text, cursor, text.length)
    return out.toString() to matches.size
  }

  /** 1-based line and column of [offset], for the status line. */
  fun position(text: String, offset: Int): Pair<Int, Int> {
    val end = offset.coerceIn(0, text.length)
    var line = 1
    var lineStart = 0
    for (i in 0 until end) {
      if (text[i] == '\n') {
        line++
        lineStart = i + 1
      }
    }
    return line to (end - lineStart + 1)
  }
}
