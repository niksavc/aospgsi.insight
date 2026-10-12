package nks.aospgsi.insight.core.verification

/** Kinds of closed-source Google software that this project must not host or sell. */
enum class ProprietaryKind {
  PLAY_STORE,
  PLAY_SERVICES,
  WEBVIEW,
  OTHER_GOOGLE_APP,
}

/**
 * Recognizes proprietary Google packages (GApps) by package ID. They are licensed by Google to
 * device makers only, so the app never distributes them: it inspects what the user already has and
 * points to the official source. Detection is by package ID alone and is advisory, not a verdict on
 * the file.
 */
object ProprietaryGoogle {
  private val exact =
    mapOf(
      "com.android.vending" to ProprietaryKind.PLAY_STORE,
      "com.google.android.finsky" to ProprietaryKind.PLAY_STORE,
      "com.google.android.gms" to ProprietaryKind.PLAY_SERVICES,
      "com.google.android.gsf" to ProprietaryKind.PLAY_SERVICES,
      "com.google.android.webview" to ProprietaryKind.WEBVIEW,
      "com.google.android.trichromelibrary" to ProprietaryKind.WEBVIEW,
      "com.android.chrome" to ProprietaryKind.OTHER_GOOGLE_APP,
    )

  fun classify(packageName: String?): ProprietaryKind? {
    if (packageName.isNullOrEmpty()) return null
    exact[packageName]?.let {
      return it
    }
    return when {
      packageName.startsWith("com.google.android.gms.") -> ProprietaryKind.PLAY_SERVICES
      packageName.startsWith("com.google.android.trichromelibrary") -> ProprietaryKind.WEBVIEW
      packageName.startsWith("com.google.android.") -> ProprietaryKind.OTHER_GOOGLE_APP
      else -> null
    }
  }
}
