package nks.aospgsi.insight.core.apkinspector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DistributionRulesTest {
  @Test
  fun playDistributionStampAndSplitsAreRead() {
    val info =
      DistributionRules.from(
        mapOf(
          "com.android.stamp.source" to "https://play.google.com/store",
          "com.android.stamp.type" to "STAMP_TYPE_DISTRIBUTION_APK",
          "com.android.vending.splits" to "2131951616",
          "com.android.vending.derived.apk.id" to "3",
        ),
      )
    assertNotNull(info)
    assertTrue(info.claimsGooglePlay)
    assertTrue(info.playSplit)
    assertEquals("3", info.derivedApkId)
    assertEquals("STAMP_TYPE_DISTRIBUTION_APK", info.stampType)
    assertFalse(info.splitsRequired)
  }

  @Test
  fun requiredFlagAndFusedModulesCount() {
    val info =
      DistributionRules.from(
        mapOf(
          "com.android.vending.splits.required" to "true",
          "com.android.dynamic.apk.fused.modules" to "base",
        ),
      )
    assertNotNull(info)
    assertTrue(info.splitsRequired)
    assertTrue(info.playSplit)
    assertFalse(info.claimsGooglePlay)
  }

  @Test
  fun unrelatedMetaDataGivesNothing() {
    assertNull(DistributionRules.from(mapOf("firebase_analytics_collection_enabled" to "false")))
    assertNull(DistributionRules.from(emptyMap()))
  }
}
