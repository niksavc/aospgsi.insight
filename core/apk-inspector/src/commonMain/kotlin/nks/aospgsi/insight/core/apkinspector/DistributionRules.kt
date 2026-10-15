package nks.aospgsi.insight.core.apkinspector

/**
 * What the manifest meta-data says about how an APK was distributed. Anyone can write these
 * entries, so they describe a claim, not proof of origin; the signing key is what matters.
 */
data class DistributionInfo(
  /** Value of com.android.stamp.source, e.g. https://play.google.com/store. */
  val stampSource: String?,
  /** Value of com.android.stamp.type, e.g. STAMP_TYPE_DISTRIBUTION_APK. */
  val stampType: String?,
  /** True when the APK is one piece of a Google Play split delivery. */
  val playSplit: Boolean,
  /** Play's id for this derived split APK, when present. */
  val derivedApkId: String?,
  /** True when Play marks the splits as required for the app to run. */
  val splitsRequired: Boolean,
) {
  val claimsGooglePlay: Boolean
    get() = stampSource?.contains("play.google.com") == true
}

object DistributionRules {
  private const val STAMP_SOURCE = "com.android.stamp.source"
  private const val STAMP_TYPE = "com.android.stamp.type"
  private const val VENDING_SPLITS = "com.android.vending.splits"
  private const val VENDING_SPLITS_REQUIRED = "com.android.vending.splits.required"
  private const val DERIVED_APK_ID = "com.android.vending.derived.apk.id"
  private const val FUSED_MODULES = "com.android.dynamic.apk.fused.modules"

  /** Null when the meta-data carries none of the distribution entries. */
  fun from(metaData: Map<String, String>): DistributionInfo? {
    val known =
      listOf(STAMP_SOURCE, STAMP_TYPE, VENDING_SPLITS, VENDING_SPLITS_REQUIRED, DERIVED_APK_ID)
    if (known.none { it in metaData } && FUSED_MODULES !in metaData) return null
    return DistributionInfo(
      stampSource = metaData[STAMP_SOURCE],
      stampType = metaData[STAMP_TYPE],
      playSplit =
        VENDING_SPLITS in metaData || DERIVED_APK_ID in metaData || FUSED_MODULES in metaData,
      derivedApkId = metaData[DERIVED_APK_ID],
      splitsRequired = metaData[VENDING_SPLITS_REQUIRED]?.equals("true", ignoreCase = true) == true,
    )
  }
}
