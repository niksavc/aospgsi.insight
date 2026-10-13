package nks.aospgsi.insight.core.apkinspector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeLibraryCatalogTest {
  @Test
  fun recognizesTheRequestedLibraries() {
    assertEquals(
      LibraryCategory.MESSAGING,
      NativeLibraryCatalog.identify("libtmessages.49.so")?.category,
    )
    assertEquals(LibraryCategory.MAPS, NativeLibraryCatalog.identify("libmaplibre.so")?.category)
    assertEquals(
      LibraryCategory.MACHINE_LEARNING,
      NativeLibraryCatalog.identify("liblanguage_id_l2c_jni.so")?.category,
    )
    assertEquals(
      LibraryCategory.ANDROIDX,
      NativeLibraryCatalog.identify("libdatastore_shared_counter.so")?.category,
    )
    assertEquals(LibraryCategory.MEDIA, NativeLibraryCatalog.identify("libyuv.so")?.category)
  }

  @Test
  fun unityLibrariesAreMarkedAsOftenShippingAds() {
    listOf("libunity.so", "libmono.so", "libil2cpp.so").forEach {
      assertTrue(NativeLibraryCatalog.identify(it)?.adsCommon == true, it)
    }
    assertFalse(NativeLibraryCatalog.identify("libyuv.so")?.adsCommon == true)
  }

  @Test
  fun libmainCountsOnlyNextToLibunity() {
    assertNull(NativeLibraryCatalog.identify("libmain.so"))
    assertNull(NativeLibraryCatalog.identify("libmain.so", setOf("libfoo.so")))
    assertEquals(
      LibraryCategory.GAME_ENGINE,
      NativeLibraryCatalog.identify("libmain.so", setOf("libunity.so", "libmain.so"))?.category,
    )
  }

  @Test
  fun identifyAllUsesTheSameAbiFolderForSiblings() {
    val found =
      NativeLibraryCatalog.identifyAll(
        listOf(
          "lib/arm64-v8a/libmain.so",
          "lib/arm64-v8a/libunity.so",
          "lib/x86/libmain.so",
          "lib/arm64-v8a/libyuv.so",
        ),
      )
    assertEquals(listOf("libmain.so", "libunity.so", "libyuv.so"), found.map { it.first })
  }

  @Test
  fun unknownLibrariesAreIgnored() {
    assertNull(NativeLibraryCatalog.identify("libsomething.so"))
  }
}
