package nks.aospgsi.insight.core.monetization

/**
 * What the app can do. Anything that keeps a user safe or able to install an app is never paid:
 * inspecting, the install check, the install itself and the single-APK fix stay free. Paid ("Pro")
 * features are conveniences for power users and organizations.
 */
enum class Feature(val pro: Boolean) {
  INSPECT(false),
  INSTALL_CHECK(false),
  INSTALL(false),
  FIX(false),
  BATCH_FIX(true),
  PRIVILEGED_BACKENDS(true),
  PROFILE_SYNC(true),
}

/** Source of truth for which [Feature]s are unlocked. */
interface Entitlements {
  fun has(feature: Feature): Boolean
}

/** Everything that is not Pro. There is no billing or license check in this build yet. */
object FreeTier : Entitlements {
  override fun has(feature: Feature): Boolean = !feature.pro
}
