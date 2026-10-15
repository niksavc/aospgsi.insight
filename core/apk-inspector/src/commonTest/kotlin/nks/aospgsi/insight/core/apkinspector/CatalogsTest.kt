package nks.aospgsi.insight.core.apkinspector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogsTest {
  @Test
  fun identifiesTheRequestedLibraries() {
    listOf(
        "lib/arm64-v8a/libtmessages.49.so",
        "lib/arm64-v8a/libtmessages.52.so",
        "lib/x86_64/libmaplibre.so",
        "lib/arm64-v8a/liblanguage_id_l2c_jni.so",
        "lib/arm64-v8a/libdatastore_shared_counter.so",
        "lib/arm64-v8a/libyuv.so",
        "lib/arm64-v8a/libunity.so",
        "lib/arm64-v8a/libmono.so",
        "lib/arm64-v8a/libmain.so",
      )
      .forEach { assertNotNull(LibraryCatalog.identify(it), it) }
  }

  @Test
  fun licensesAndProprietaryFlagsAreSet() {
    assertEquals("GPL-2.0", LibraryCatalog.identify("libtmessages.49.so")?.license)
    assertTrue(LibraryCatalog.identify("liblanguage_id_l2c_jni.so")!!.proprietary)
    assertTrue(LibraryCatalog.identify("libunity.so")!!.proprietary)
    assertFalse(LibraryCatalog.identify("libyuv.so")!!.proprietary)
    assertFalse(LibraryCatalog.identify("libmono.so")!!.proprietary)
  }

  @Test
  fun unknownLibrariesAreNotGuessed() {
    assertNull(LibraryCatalog.identify("lib/arm64-v8a/libsomething.so"))
    assertNull(LibraryCatalog.identify("libtmessages.so"))
  }

  @Test
  fun unityNeedsLibunityNotJustLibmain() {
    assertNull(LibraryCatalog.detectFramework(listOf("lib/arm64-v8a/libmain.so")))
    assertEquals(
      "Unity (Mono)",
      LibraryCatalog.detectFramework(
        listOf("lib/arm64-v8a/libunity.so", "lib/arm64-v8a/libmono.so", "lib/arm64-v8a/libmain.so"),
      ),
    )
    assertEquals(
      "Unity (IL2CPP)",
      LibraryCatalog.detectFramework(listOf("libunity.so", "libil2cpp.so", "libmain.so")),
    )
  }

  @Test
  fun adSdksComeFromComponentsAndMetaData() {
    val found =
      AdSdkCatalog.detect(
        componentNames =
          listOf(
            "com.unity3d.services.ads.adunit.AdUnitActivity",
            "com.google.android.gms.ads.AdActivity",
            "com.example.MainActivity",
          ),
        metaDataKeys = listOf("com.yandex.mobile.ads.AUTOMATIC_SDK_INITIALIZATION", "other"),
      )
    assertEquals(listOf("Google AdMob", "Unity Ads", "Yandex Mobile Ads"), found)
  }

  @Test
  fun appWithoutAdsFindsNothing() {
    assertEquals(
      emptyList(),
      AdSdkCatalog.detect(
        listOf("com.example.MainActivity"),
        listOf("firebase_analytics_collection_enabled"),
      ),
    )
  }
}
