package nks.aospgsi.insight.core.monetization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntitlementsTest {
  @Test
  fun safetyAndInstallFeaturesAreNeverPaid() {
    listOf(Feature.INSPECT, Feature.INSTALL_CHECK, Feature.INSTALL, Feature.FIX).forEach {
      assertFalse(it.pro, "$it must stay free")
      assertTrue(FreeTier.has(it))
    }
  }

  @Test
  fun freeTierLacksProFeatures() {
    Feature.entries.filter { it.pro }.forEach { assertFalse(FreeTier.has(it)) }
  }

  @Test
  fun onlyHttpsLinksWithLabelsAreKept() {
    val links =
      listOf(
        SupportLink("Sponsors", "https://github.com/sponsors/example"),
        SupportLink("", "https://example.org"),
        SupportLink("Plain", "http://example.org"),
      )
    assertEquals(listOf(links[0]), ProjectLinks.valid(links))
  }
}
