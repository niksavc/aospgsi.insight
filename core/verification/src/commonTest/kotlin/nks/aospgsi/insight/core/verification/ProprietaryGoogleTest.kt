package nks.aospgsi.insight.core.verification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProprietaryGoogleTest {
  @Test
  fun recognizesPlayStoreServicesAndWebView() {
    assertEquals(ProprietaryKind.PLAY_STORE, ProprietaryGoogle.classify("com.android.vending"))
    assertEquals(
      ProprietaryKind.PLAY_STORE,
      ProprietaryGoogle.classify("com.google.android.finsky"),
    )
    assertEquals(
      ProprietaryKind.PLAY_SERVICES,
      ProprietaryGoogle.classify("com.google.android.gms"),
    )
    assertEquals(ProprietaryKind.WEBVIEW, ProprietaryGoogle.classify("com.google.android.webview"))
  }

  @Test
  fun otherGoogleAppsAreFlaggedByPrefix() {
    assertEquals(
      ProprietaryKind.OTHER_GOOGLE_APP,
      ProprietaryGoogle.classify("com.google.android.apps.maps"),
    )
  }

  @Test
  fun openSourceAndUnknownPackagesAreNotFlagged() {
    assertNull(ProprietaryGoogle.classify("com.aurora.store"))
    assertNull(ProprietaryGoogle.classify("org.fdroid.fdroid"))
    assertNull(ProprietaryGoogle.classify("com.roblox.client"))
    assertNull(ProprietaryGoogle.classify(null))
    assertNull(ProprietaryGoogle.classify(""))
  }
}
