package nks.aospgsi.insight.core.apkinspector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import nks.aospgsi.insight.core.model.ResourceKind

class ResourceRulesTest {
  @Test
  fun lottieHeaderIsRecognized() {
    val head = """{"v":"5.7.4","fr":60,"ip":0,"op":120,"w":512,"h":512,"nm":"star","layers":[]}"""
    assertEquals(ResourceKind.LOTTIE, ResourceRules.kindOf(head))
  }

  @Test
  fun plainJsonIsNotLottie() {
    assertEquals(ResourceKind.JSON, ResourceRules.kindOf("""{"name":"config","w":1}"""))
    assertEquals(ResourceKind.JSON, ResourceRules.kindOf("[]"))
  }

  @Test
  fun onlyJsonOutsideMetaInfIsListed() {
    assertTrue(ResourceRules.isJsonResource("res/raw/swatch.json"))
    assertTrue(ResourceRules.isJsonResource("assets/data/Config.JSON"))
    assertFalse(ResourceRules.isJsonResource("META-INF/something.json"))
    assertFalse(ResourceRules.isJsonResource("res/raw/sound.ogg"))
  }
}
