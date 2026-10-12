package nks.aospgsi.insight.core.patchapi

import nks.aospgsi.insight.core.model.ApkInspection

enum class FixUnavailableReason {
  /** Split bundles are signed as a set; patching only the base APK would break them. */
  BUNDLE,
  NO_PACKAGE_NAME,
  /** The APK already meets the device's minimum and no clone was requested. */
  NOTHING_TO_FIX,
}

sealed interface FixPlan {
  /**
   * @property targetSdk new target SDK, or null when the target SDK stays as it is.
   * @property newPackage new package ID for a clone, or null to keep the original ID.
   */
  data class Ready(
    val patches: List<ApkPatch>,
    val targetSdk: Int?,
    val newPackage: String?,
  ) : FixPlan

  data class Unavailable(val reason: FixUnavailableReason) : FixPlan
}

/**
 * Plans the smallest set of changes that lets an old APK install on a recent Android: raise the
 * target SDK to the lowest value the device accepts, and optionally re-root the app under a new
 * package ID so it installs next to an original that is signed with a different key.
 */
object FixPlanner {
  const val CLONE_SUFFIX = ".aospgsi"

  /**
   * @param minimumTargetSdk lowest targetSdkVersion the device installs, or null when it enforces
   *   none.
   * @param cloneAlongside install under a new package ID instead of replacing the original.
   */
  fun plan(
    apk: ApkInspection,
    minimumTargetSdk: Int?,
    cloneAlongside: Boolean,
  ): FixPlan {
    if (apk.isBundle) return FixPlan.Unavailable(FixUnavailableReason.BUNDLE)
    val packageName = apk.packageName
    if (packageName.isNullOrBlank()) {
      return FixPlan.Unavailable(FixUnavailableReason.NO_PACKAGE_NAME)
    }

    val patches = mutableListOf<ApkPatch>()

    val target = apk.targetSdk
    // The target SDK may never end up below the min SDK, or the manifest would be invalid.
    val newTarget =
      if (minimumTargetSdk != null && target != null && target < minimumTargetSdk) {
        maxOf(minimumTargetSdk, apk.minSdk ?: 0)
      } else {
        null
      }
    if (newTarget != null) patches += ApkPatch.SetTargetSdk(newTarget)

    val newPackage = if (cloneAlongside) cloneName(packageName) else null
    if (newPackage != null) patches += ApkPatch.SetPackageName(newPackage)

    if (patches.isEmpty()) return FixPlan.Unavailable(FixUnavailableReason.NOTHING_TO_FIX)
    return FixPlan.Ready(patches, newTarget, newPackage)
  }

  fun cloneName(packageName: String): String =
    if (packageName.endsWith(CLONE_SUFFIX)) packageName else packageName + CLONE_SUFFIX
}
