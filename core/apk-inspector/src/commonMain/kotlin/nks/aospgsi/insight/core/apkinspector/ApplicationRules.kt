package nks.aospgsi.insight.core.apkinspector

import nks.aospgsi.insight.core.model.ApplicationAttributes

/**
 * Turns ApplicationInfo flag bits into [ApplicationAttributes]. The bit values are fixed parts of
 * the Android platform ABI, so they are spelled out here and the logic can be tested on the host.
 */
object ApplicationRules {
  const val FLAG_DEBUGGABLE = 1 shl 1
  const val FLAG_HAS_CODE = 1 shl 2
  const val FLAG_PERSISTENT = 1 shl 3
  const val FLAG_TEST_ONLY = 1 shl 8
  const val FLAG_ALLOW_BACKUP = 1 shl 15
  const val FLAG_LARGE_HEAP = 1 shl 20
  const val FLAG_SUPPORTS_RTL = 1 shl 22
  const val FLAG_USES_CLEARTEXT_TRAFFIC = 1 shl 27
  const val FLAG_EXTRACT_NATIVE_LIBS = 1 shl 28
  const val FLAG_HARDWARE_ACCELERATED = 1 shl 29

  /** android:appCategory enum names by ApplicationInfo.category value. */
  private val categories =
    mapOf(
      0 to "game",
      1 to "audio",
      2 to "video",
      3 to "image",
      4 to "social",
      5 to "news",
      6 to "maps",
      7 to "productivity",
      8 to "accessibility",
    )

  fun fromFlags(
    flags: Int,
    targetSdk: Int,
    applicationClass: String?,
    backupAgent: String?,
    process: String?,
    category: Int?,
  ): ApplicationAttributes {
    fun has(flag: Int) = (flags and flag) != 0
    return ApplicationAttributes(
      debuggable = has(FLAG_DEBUGGABLE),
      allowBackup = has(FLAG_ALLOW_BACKUP),
      usesCleartextTraffic = has(FLAG_USES_CLEARTEXT_TRAFFIC),
      largeHeap = has(FLAG_LARGE_HEAP),
      hasCode = has(FLAG_HAS_CODE),
      extractNativeLibs = has(FLAG_EXTRACT_NATIVE_LIBS),
      testOnly = has(FLAG_TEST_ONLY),
      persistent = has(FLAG_PERSISTENT),
      supportsRtl = has(FLAG_SUPPORTS_RTL),
      hardwareAccelerated = has(FLAG_HARDWARE_ACCELERATED),
      applicationClass = applicationClass?.takeIf { it.isNotEmpty() },
      backupAgent = backupAgent?.takeIf { it.isNotEmpty() },
      process = process?.takeIf { it.isNotEmpty() },
      category = category?.let { categories[it] },
    )
  }

  /** Things worth telling the user before they install or fix the APK. */
  fun warnings(app: ApplicationAttributes): List<String> = buildList {
    if (app.testOnly) {
      add(
        "The app is marked testOnly: Android refuses to install it unless it is installed with " +
          "the test flag (adb install -t).",
      )
    }
    if (app.debuggable) {
      add("The app is debuggable: anyone with a connected computer can inspect and alter it.")
    }
  }
}
