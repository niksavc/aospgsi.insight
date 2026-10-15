package nks.aospgsi.insight.core.apkinspector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplicationRulesTest {
  private fun attrs(flags: Int, category: Int? = null) =
    ApplicationRules.fromFlags(flags, 34, ".App", "", ":remote", category)

  @Test
  fun flagBitsBecomeAttributes() {
    val flags =
      ApplicationRules.FLAG_DEBUGGABLE or
        ApplicationRules.FLAG_HAS_CODE or
        ApplicationRules.FLAG_ALLOW_BACKUP or
        ApplicationRules.FLAG_EXTRACT_NATIVE_LIBS
    val app = attrs(flags)
    assertTrue(app.debuggable)
    assertTrue(app.hasCode)
    assertTrue(app.allowBackup)
    assertTrue(app.extractNativeLibs)
    assertFalse(app.largeHeap)
    assertFalse(app.usesCleartextTraffic)
    assertFalse(app.testOnly)
  }

  @Test
  fun emptyStringsBecomeNull() {
    val app = attrs(0)
    assertEquals(".App", app.applicationClass)
    assertNull(app.backupAgent)
    assertEquals(":remote", app.process)
  }

  @Test
  fun categoryIsMappedByValue() {
    assertEquals("game", attrs(0, 0).category)
    assertEquals("productivity", attrs(0, 7).category)
    assertNull(attrs(0, -1).category)
    assertNull(attrs(0, null).category)
  }

  @Test
  fun testOnlyAndDebuggableProduceWarnings() {
    assertEquals(0, ApplicationRules.warnings(attrs(ApplicationRules.FLAG_HAS_CODE)).size)
    val both =
      ApplicationRules.warnings(
        attrs(ApplicationRules.FLAG_TEST_ONLY or ApplicationRules.FLAG_DEBUGGABLE),
      )
    assertEquals(2, both.size)
    assertTrue(both.first().contains("testOnly"))
  }
}
