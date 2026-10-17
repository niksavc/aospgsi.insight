package nks.aospgsi.insight.core.texteditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TextSearchTest {
  @Test
  fun findsEveryNonOverlappingMatch() {
    assertEquals(listOf(0, 2), TextSearch.findAll("aaaa", "aa", ignoreCase = false))
    assertEquals(listOf(0, 4, 8), TextSearch.findAll("Foo foo FOO", "foo", ignoreCase = true))
    assertEquals(listOf(4), TextSearch.findAll("Foo foo FOO", "foo", ignoreCase = false))
    assertEquals(emptyList(), TextSearch.findAll("abc", "", ignoreCase = false))
  }

  @Test
  fun nextWrapsAround() {
    val text = "one two one"
    assertEquals(0, TextSearch.next(text, "one", 0, false))
    assertEquals(8, TextSearch.next(text, "one", 1, false))
    assertEquals(0, TextSearch.next(text, "one", 9, false))
    assertNull(TextSearch.next(text, "zzz", 0, false))
    assertNull(TextSearch.next(text, "", 0, false))
  }

  @Test
  fun previousWrapsAround() {
    val text = "one two one"
    assertEquals(0, TextSearch.previous(text, "one", 8, false))
    assertEquals(8, TextSearch.previous(text, "one", 0, false))
    assertEquals(8, TextSearch.previous(text, "one", 11, false))
    assertNull(TextSearch.previous(text, "zzz", 5, false))
  }

  @Test
  fun queryLongerThanTheTextFindsNothing() {
    assertNull(TextSearch.next("ab", "abc", 0, false))
    assertNull(TextSearch.previous("ab", "abc", 2, false))
  }

  @Test
  fun replaceAllCountsAndRewrites() {
    assertEquals("x-x-x" to 3, TextSearch.replaceAll("a-a-a", "a", "x", false))
    assertEquals("ab" to 0, TextSearch.replaceAll("ab", "z", "y", false))
    assertEquals("ba" to 1, TextSearch.replaceAll("aaa", "aa", "b", false))
  }

  @Test
  fun replacementMayContainTheQuery() {
    assertEquals("aa-aa" to 2, TextSearch.replaceAll("a-a", "a", "aa", false))
  }

  @Test
  fun positionIsOneBasedLineAndColumn() {
    val text = "ab\ncde\nf"
    assertEquals(1 to 1, TextSearch.position(text, 0))
    assertEquals(1 to 3, TextSearch.position(text, 2))
    assertEquals(2 to 1, TextSearch.position(text, 3))
    assertEquals(2 to 3, TextSearch.position(text, 5))
    assertEquals(3 to 2, TextSearch.position(text, 8))
  }
}
