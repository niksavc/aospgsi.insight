package nks.aospgsi.insight.core.apkinspector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SdkCatalogTest {
  private fun detect(
    components: List<String> = emptyList(),
    meta: List<String> = emptyList(),
    permissions: List<String> = emptyList(),
  ) = SdkCatalog.detect(components, meta, permissions)

  @Test
  fun findsSdksFromComponents() {
    val names =
      detect(
          components =
            listOf(
              "com.google.firebase.messaging.FirebaseMessagingService",
              "io.sentry.android.core.SentryInitProvider",
              "androidx.work.impl.background.systemjob.SystemJobService",
            ),
        )
        .map { it.name }
    assertEquals(setOf("Firebase Cloud Messaging", "Sentry", "WorkManager"), names.toSet())
  }

  @Test
  fun findsSdksFromMetaDataAndPermissions() {
    val found =
      detect(
        meta = listOf("com.facebook.sdk.ApplicationId", "firebase_crashlytics_collection_enabled"),
        permissions = listOf("com.android.vending.BILLING", "com.android.vending.CHECK_LICENSE"),
      )
    val byName = found.associateBy { it.name }
    assertEquals(EvidenceKind.META_DATA, byName["Facebook SDK"]?.evidence)
    assertEquals(SdkCategory.CRASH_REPORTING, byName["Firebase Crashlytics"]?.category)
    assertEquals(EvidenceKind.PERMISSION, byName["Google Play Billing"]?.evidence)
    assertEquals(SdkCategory.LICENSING, byName["Google Play license check (LVL)"]?.category)
  }

  @Test
  fun oneMatchPerSdkKeepsFirstEvidence() {
    val found =
      detect(
        components = listOf("com.android.billingclient.api.ProxyBillingActivity"),
        permissions = listOf("com.android.vending.BILLING"),
      )
    val billing = found.filter { it.name == "Google Play Billing" }
    assertEquals(1, billing.size)
    assertEquals(EvidenceKind.COMPONENT, billing.single().evidence)
  }

  @Test
  fun prefixMatchesPermissionFamilies() {
    val found = detect(permissions = listOf("android.permission.ACCESS_ADSERVICES_TOPICS"))
    assertEquals(SdkCategory.ADVERTISING_ID, found.single().category)
  }

  @Test
  fun reactNativeIsNotTakenForFacebookSdk() {
    val found = detect(components = listOf("com.facebook.react.devsupport.DevSettingsActivity"))
    assertEquals(listOf("React Native"), found.map { it.name })
  }

  @Test
  fun identifiesASingleComponentForABadge() {
    assertEquals(
      "Process Phoenix",
      SdkCatalog.identify("com.jakewharton.processphoenix.PhoenixActivity")?.name,
    )
    assertEquals("Shizuku", SdkCatalog.identify("rikka.shizuku.ShizukuProvider")?.name)
    assertEquals(
      "Firebase",
      SdkCatalog.identify("com.google.firebase.sessions.SessionLifecycleService")?.name,
    )
    assertEquals(
      "Firebase Analytics",
      SdkCatalog.identify("com.google.android.gms.measurement.AppMeasurementService")?.name,
    )
    assertEquals(
      "Room",
      SdkCatalog.identify("androidx.room.MultiInstanceInvalidationService")?.name,
    )
    assertEquals("FileProvider", SdkCatalog.identify("androidx.core.content.FileProvider")?.name)
    assertNull(SdkCatalog.identify(".features.home.ui.MainActivity"))
  }

  @Test
  fun specificFirebaseRulesWinOverTheGenericOne() {
    assertEquals(
      "Firebase Cloud Messaging",
      SdkCatalog.identify("com.google.firebase.messaging.FirebaseMessagingService")?.name,
    )
    assertEquals(
      "Firebase Crashlytics",
      SdkCatalog.identify("com.google.firebase.crashlytics.internal.Foo")?.name,
    )
  }

  @Test
  fun shizukuIsFoundFromMetaDataAndPermission() {
    val found =
      detect(
        meta = listOf("moe.shizuku.client.V3_SUPPORT"),
        permissions = listOf("moe.shizuku.manager.permission.API_V23"),
      )
    assertEquals(listOf("Shizuku"), found.map { it.name })
  }

  @Test
  fun unknownAndEmptyInputGiveNothing() {
    assertTrue(detect().isEmpty())
    assertTrue(detect(components = listOf("com.example.MainActivity")).isEmpty())
  }

  @Test
  fun newNativeLibrariesAreRecognized() {
    listOf(
        "lib/arm64-v8a/libsentry.so",
        "lib/arm64-v8a/libcrashlytics-handler.so",
        "lib/arm64-v8a/libhermes.so",
        "lib/arm64-v8a/libgodot_android.so",
        "lib/arm64-v8a/libavcodec.so",
        "lib/arm64-v8a/libsqlcipher.so",
        "lib/arm64-v8a/libmmkv.so",
      )
      .forEach { assertNotNull(LibraryCatalog.identify(it), it) }
    assertTrue(LibraryCatalog.identify("libcrashlytics.so")!!.proprietary)
    assertNull(LibraryCatalog.identify("libavfoo.so"))
  }
}

class AbiRulesTest {
  @Test
  fun supportClassifiesBitness() {
    assertEquals(AbiSupport.NO_NATIVE, AbiRules.support(emptyList()))
    assertEquals(AbiSupport.ONLY_32_BIT, AbiRules.support(listOf("armeabi-v7a", "x86")))
    assertEquals(AbiSupport.ONLY_64_BIT, AbiRules.support(listOf("arm64-v8a")))
    assertEquals(AbiSupport.BOTH, AbiRules.support(listOf("arm64-v8a", "armeabi-v7a")))
  }

  @Test
  fun thirtyTwoBitOnlyAppOnSixtyFourBitOnlyDeviceIsFlagged() {
    val warning = AbiRules.deviceWarning(listOf("armeabi-v7a"), listOf("arm64-v8a"))
    assertNotNull(warning)
    assertTrue("armeabi-v7a" in warning && "arm64-v8a" in warning)
  }

  @Test
  fun compatibleOrUnknownGivesNoWarning() {
    assertNull(AbiRules.deviceWarning(listOf("armeabi-v7a"), listOf("arm64-v8a", "armeabi-v7a")))
    assertNull(AbiRules.deviceWarning(emptyList(), listOf("arm64-v8a")))
    assertNull(AbiRules.deviceWarning(listOf("arm64-v8a"), emptyList()))
    assertEquals(null, AbiRules.runsOnDevice(emptyList(), listOf("x86_64")))
    assertEquals(true, AbiRules.runsOnDevice(listOf("x86_64"), listOf("x86_64", "x86")))
  }
}

class BuildTagRulesTest {
  @Test
  fun recognizesTagsFromEntryNames() {
    val tags =
      BuildTagRules.fromEntryNames(
        sequenceOf(
          "classes.dex",
          "classes2.dex",
          "META-INF/app_release.kotlin_module",
          "META-INF/androidx.compose.ui_ui.version",
          "META-INF/com/android/build/gradle/app-metadata.properties",
        ),
      )
    assertEquals(listOf("Java/DEX", "Kotlin", "Jetpack Compose", "Android Gradle Plugin"), tags)
  }

  @Test
  fun nestedDexAndPlainFilesGiveNothing() {
    assertEquals(emptyList(), BuildTagRules.fromEntryNames(sequenceOf("assets/x.dex", "res/a.png")))
  }
}
