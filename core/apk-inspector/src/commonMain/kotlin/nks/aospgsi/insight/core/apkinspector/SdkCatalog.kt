package nks.aospgsi.insight.core.apkinspector

import nks.aospgsi.insight.core.apkinspector.EvidenceKind.COMPONENT
import nks.aospgsi.insight.core.apkinspector.EvidenceKind.META_DATA
import nks.aospgsi.insight.core.apkinspector.EvidenceKind.PERMISSION
import nks.aospgsi.insight.core.apkinspector.SdkCategory.ADVERTISING_ID
import nks.aospgsi.insight.core.apkinspector.SdkCategory.ANALYTICS
import nks.aospgsi.insight.core.apkinspector.SdkCategory.ATTRIBUTION
import nks.aospgsi.insight.core.apkinspector.SdkCategory.CRASH_REPORTING
import nks.aospgsi.insight.core.apkinspector.SdkCategory.ENGAGEMENT
import nks.aospgsi.insight.core.apkinspector.SdkCategory.FRAMEWORK
import nks.aospgsi.insight.core.apkinspector.SdkCategory.GOOGLE_SERVICES
import nks.aospgsi.insight.core.apkinspector.SdkCategory.HUAWEI_SERVICES
import nks.aospgsi.insight.core.apkinspector.SdkCategory.JETPACK
import nks.aospgsi.insight.core.apkinspector.SdkCategory.LIBRARY
import nks.aospgsi.insight.core.apkinspector.SdkCategory.LICENSING
import nks.aospgsi.insight.core.apkinspector.SdkCategory.PAYMENTS
import nks.aospgsi.insight.core.apkinspector.SdkCategory.PUSH
import nks.aospgsi.insight.core.apkinspector.SdkCategory.SOCIAL

enum class SdkCategory {
  ANALYTICS,
  CRASH_REPORTING,
  PUSH,
  ATTRIBUTION,
  ENGAGEMENT,
  PAYMENTS,
  LICENSING,
  ADVERTISING_ID,
  SOCIAL,
  GOOGLE_SERVICES,
  HUAWEI_SERVICES,
  FRAMEWORK,
  JETPACK,
  LIBRARY,
}

/** What in the APK pointed at the SDK. */
enum class EvidenceKind {
  COMPONENT,
  META_DATA,
  PERMISSION,
}

data class SdkMatch(
  val name: String,
  val category: SdkCategory,
  val evidence: EvidenceKind,
  /** The class name, meta-data key or permission that matched. */
  val matched: String,
)

/**
 * Rule-based SDK detection in the spirit of LibChecker: a rule matches a component class prefix, a
 * meta-data key prefix or a requested permission. The rules are written from public SDK
 * documentation; nothing is copied from another project. Advertising SDKs live in [AdSdkCatalog]
 * and native libraries in [LibraryCatalog].
 *
 * Only the manifest is read, never the app's code, so this is a lower bound: an SDK that declares
 * nothing in the manifest is not found, and a match shows that a component exists, not that it is
 * used at runtime.
 */
object SdkCatalog {
  private class Rule(
    val kind: EvidenceKind,
    val prefix: String,
    val name: String,
    val category: SdkCategory,
  )

  private fun c(prefix: String, name: String, category: SdkCategory) =
    Rule(COMPONENT, prefix, name, category)

  private fun m(prefix: String, name: String, category: SdkCategory) =
    Rule(META_DATA, prefix, name, category)

  private fun p(name: String, sdk: String, category: SdkCategory) =
    Rule(PERMISSION, name, sdk, category)

  private val rules =
    listOf(
      // Google / Firebase
      c("com.google.firebase.messaging", "Firebase Cloud Messaging", PUSH),
      c("com.google.firebase.crashlytics", "Firebase Crashlytics", CRASH_REPORTING),
      c("com.google.firebase.analytics", "Firebase Analytics", ANALYTICS),
      c("com.google.android.gms.measurement", "Google Analytics for Firebase", ANALYTICS),
      c("com.google.firebase.perf", "Firebase Performance", ANALYTICS),
      c("com.google.firebase", "Firebase", GOOGLE_SERVICES),
      c(
        "com.google.android.gms.common.api.GoogleApiActivity",
        "Google Play services client",
        GOOGLE_SERVICES,
      ),
      c("com.google.android.gms.auth", "Google Sign-In", GOOGLE_SERVICES),
      c("com.android.billingclient", "Google Play Billing", PAYMENTS),
      c(
        "com.google.android.play.core.missingsplits",
        "Play Core missing-splits check",
        GOOGLE_SERVICES,
      ),
      m("com.google.android.gms.version", "Google Play services client", GOOGLE_SERVICES),
      m("com.google.firebase.messaging.default_notification", "Firebase Cloud Messaging", PUSH),
      m("firebase_crashlytics_collection_enabled", "Firebase Crashlytics", CRASH_REPORTING),
      m("firebase_analytics_collection_enabled", "Firebase Analytics", ANALYTICS),
      m("google_analytics_adid_collection_enabled", "Firebase Analytics", ANALYTICS),
      m("com.google.android.play.billingclient.version", "Google Play Billing", PAYMENTS),
      c("com.google.android.datatransport", "Google Data Transport", GOOGLE_SERVICES),
      // Open-source libraries
      c("com.jakewharton.processphoenix", "Process Phoenix", LIBRARY),
      c("com.mikepenz.aboutlibraries", "AboutLibraries", LIBRARY),
      c("rikka.shizuku", "Shizuku", LIBRARY),
      m("moe.shizuku", "Shizuku", LIBRARY),
      p("moe.shizuku.manager.permission", "Shizuku", LIBRARY),
      // Crash reporting, analytics, attribution, engagement
      c("io.sentry", "Sentry", CRASH_REPORTING),
      m("io.sentry.dsn", "Sentry", CRASH_REPORTING),
      c("com.amplitude", "Amplitude", ANALYTICS),
      c("com.mixpanel", "Mixpanel", ANALYTICS),
      c("io.appmetrica", "AppMetrica", ANALYTICS),
      c("com.yandex.metrica", "AppMetrica", ANALYTICS),
      c("com.flurry", "Flurry", ANALYTICS),
      c("com.adjust.sdk", "Adjust", ATTRIBUTION),
      c("com.appsflyer", "AppsFlyer", ATTRIBUTION),
      c("io.branch", "Branch", ATTRIBUTION),
      m("io.branch.sdk.BranchKey", "Branch", ATTRIBUTION),
      c("com.onesignal", "OneSignal", PUSH),
      c("com.braze", "Braze", ENGAGEMENT),
      c("com.appboy", "Braze", ENGAGEMENT),
      // Payments and social sign-in
      c("com.stripe", "Stripe", PAYMENTS),
      c("com.facebook.FacebookActivity", "Facebook SDK", SOCIAL),
      c("com.facebook.CustomTabActivity", "Facebook SDK", SOCIAL),
      c("com.facebook.FacebookContentProvider", "Facebook SDK", SOCIAL),
      m("com.facebook.sdk.ApplicationId", "Facebook SDK", SOCIAL),
      c("com.vk.api", "VK SDK", SOCIAL),
      c("com.vk.sdk", "VK SDK", SOCIAL),
      c("com.vk.auth", "VK SDK", SOCIAL),
      m("com.vk.sdk.AppId", "VK SDK", SOCIAL),
      c("com.yandex.authsdk", "Yandex Login SDK", SOCIAL),
      c("com.yandex.passport", "Yandex Passport", SOCIAL),
      // Huawei
      c("com.huawei.hms", "Huawei Mobile Services", HUAWEI_SERVICES),
      m("com.huawei.hms", "Huawei Mobile Services", HUAWEI_SERVICES),
      // App frameworks
      c("com.unity3d.player", "Unity", FRAMEWORK),
      c("io.flutter.embedding", "Flutter", FRAMEWORK),
      m("flutterEmbedding", "Flutter", FRAMEWORK),
      c("com.facebook.react", "React Native", FRAMEWORK),
      c("org.godotengine", "Godot", FRAMEWORK),
      c("org.cocos2dx", "Cocos2d-x", FRAMEWORK),
      c("mono.android", "Xamarin / .NET for Android", FRAMEWORK),
      c("org.apache.cordova", "Apache Cordova", FRAMEWORK),
      c("com.getcapacitor", "Capacitor", FRAMEWORK),
      // Jetpack
      c("androidx.core.content.FileProvider", "FileProvider", JETPACK),
      c("androidx.work", "WorkManager", JETPACK),
      c("androidx.startup", "App Startup", JETPACK),
      c("androidx.camera", "CameraX", JETPACK),
      c("androidx.room", "Room", JETPACK),
      c("androidx.profileinstaller", "Profile Installer", JETPACK),
      // Permissions that name a service
      p("com.android.vending.BILLING", "Google Play Billing", PAYMENTS),
      p("com.android.vending.CHECK_LICENSE", "Google Play license check (LVL)", LICENSING),
      p("com.google.android.c2dm.permission.RECEIVE", "Push via FCM / C2DM", PUSH),
      p("com.google.android.gms.permission.AD_ID", "Advertising ID access", ADVERTISING_ID),
      p("android.permission.ACCESS_ADSERVICES_", "Privacy Sandbox ads APIs", ADVERTISING_ID),
      p(
        "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE",
        "Play Install Referrer",
        ATTRIBUTION,
      ),
    )

  /** The SDK a single component class belongs to, or null. For a badge next to a list row. */
  fun identify(componentName: String): SdkMatch? =
    rules
      .firstOrNull { it.kind == COMPONENT && componentName.startsWith(it.prefix) }
      ?.let { SdkMatch(it.name, it.category, COMPONENT, componentName) }

  /** One match per SDK name, taking the first piece of evidence, sorted by category and name. */
  fun detect(
    componentNames: Collection<String>,
    metaDataKeys: Collection<String>,
    permissions: Collection<String>,
  ): List<SdkMatch> {
    val found = linkedMapOf<String, SdkMatch>()

    // Each value is credited to the first rule that matches it, so a specific rule (Firebase Cloud
    // Messaging) is not repeated by a broader one (Firebase).
    fun scan(kind: EvidenceKind, values: Collection<String>) {
      for (value in values) {
        val rule = rules.firstOrNull { it.kind == kind && value.startsWith(it.prefix) } ?: continue
        if (rule.name !in found) found[rule.name] = SdkMatch(rule.name, rule.category, kind, value)
      }
    }

    scan(COMPONENT, componentNames)
    scan(META_DATA, metaDataKeys)
    scan(PERMISSION, permissions)
    return found.values.sortedWith(compareBy({ it.category }, { it.name }))
  }
}
