package nks.aospgsi.insight.core.apkinspector

/**
 * Finds advertising SDKs from what the manifest declares (component class names and meta-data
 * keys). The app's code is not scanned, so an SDK that declares nothing in the manifest is missed;
 * treat the result as a lower bound.
 */
object AdSdkCatalog {
  private val classPrefixes =
    listOf(
      "com.google.android.gms.ads" to "Google AdMob",
      "com.google.android.libraries.ads" to "Google Mobile Ads",
      "com.unity3d.ads" to "Unity Ads",
      "com.unity3d.services" to "Unity Ads",
      "com.applovin" to "AppLovin",
      "com.ironsource" to "ironSource",
      "com.unity3d.mediation" to "Unity LevelPlay",
      "com.vungle" to "Vungle / Liftoff",
      "com.chartboost" to "Chartboost",
      "com.facebook.ads" to "Meta Audience Network",
      "com.yandex.mobile.ads" to "Yandex Mobile Ads",
      "com.mbridge" to "Mintegral",
      "com.bytedance.sdk.openadsdk" to "Pangle",
      "com.inmobi" to "InMobi",
      "com.adcolony" to "AdColony",
      "com.fyber" to "Fyber",
      "com.startapp" to "StartApp",
      "com.my.target" to "myTarget",
    )

  private val metaDataKeys =
    listOf(
      "com.google.android.gms.ads" to "Google AdMob",
      "applovin.sdk.key" to "AppLovin",
      "com.yandex.mobile.ads" to "Yandex Mobile Ads",
      "com.facebook.sdk.ApplicationId" to null,
    )

  fun detect(componentNames: Collection<String>, metaDataKeys: Collection<String>): List<String> {
    val found = sortedSetOf<String>()
    for (name in componentNames) {
      classPrefixes.firstOrNull { name.startsWith(it.first) }?.let { found += it.second }
    }
    for (key in metaDataKeys) {
      this.metaDataKeys.firstOrNull { key.startsWith(it.first) }?.second?.let { found += it }
    }
    return found.toList()
  }
}
