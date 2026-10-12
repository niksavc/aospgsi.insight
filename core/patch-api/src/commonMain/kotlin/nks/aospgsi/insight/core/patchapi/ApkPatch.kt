package nks.aospgsi.insight.core.patchapi

/**
 * One declarative change applied to an APK's manifest or metadata. Patches carry data only; the
 * engine decides how to apply them.
 */
sealed interface ApkPatch {
  /**
   * Re-roots the app under a new package ID so it installs next to the original (a clone). Relative
   * component names are expanded first, and provider authorities and app-defined permissions that
   * start with the old package are renamed too.
   */
  data class SetPackageName(val newPackage: String) : ApkPatch

  /** Changes the declared target SDK. This alters runtime behavior of the app, not just a flag. */
  data class SetTargetSdk(val sdk: Int) : ApkPatch

  data class SetMinSdk(val sdk: Int) : ApkPatch

  data class SetDebuggable(val debuggable: Boolean) : ApkPatch

  /** Replaces the launcher label with a literal string. */
  data class SetAppLabel(val label: String) : ApkPatch

  data class AddPermission(val name: String) : ApkPatch

  data class RemovePermission(val name: String) : ApkPatch

  data class SetVersion(val versionCode: Int?, val versionName: String?) : ApkPatch
}

object PatchValidation {
  private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
  private val PERMISSION_NAME = Regex("[A-Za-z0-9_.]+")
  private const val MAX_SDK = 40

  /** Returns human-readable problems; an empty list means the patches can be applied. */
  fun validate(patches: List<ApkPatch>): List<String> {
    val problems = mutableListOf<String>()

    fun singleton(name: String, count: Int) {
      if (count > 1) problems += "Only one $name patch is allowed."
    }
    singleton("package name", patches.count { it is ApkPatch.SetPackageName })
    singleton("target SDK", patches.count { it is ApkPatch.SetTargetSdk })
    singleton("min SDK", patches.count { it is ApkPatch.SetMinSdk })
    singleton("debuggable", patches.count { it is ApkPatch.SetDebuggable })
    singleton("label", patches.count { it is ApkPatch.SetAppLabel })
    singleton("version", patches.count { it is ApkPatch.SetVersion })

    for (patch in patches) {
      when (patch) {
        is ApkPatch.SetPackageName ->
          if (!PACKAGE_NAME.matches(patch.newPackage)) {
            problems += "Invalid package name: ${patch.newPackage}"
          }
        is ApkPatch.SetTargetSdk -> checkSdk("Target", patch.sdk, problems)
        is ApkPatch.SetMinSdk -> checkSdk("Min", patch.sdk, problems)
        is ApkPatch.SetAppLabel ->
          if (patch.label.isBlank()) problems += "The app label must not be blank."
        is ApkPatch.AddPermission ->
          if (!PERMISSION_NAME.matches(patch.name)) problems += "Invalid permission: ${patch.name}"
        is ApkPatch.RemovePermission ->
          if (!PERMISSION_NAME.matches(patch.name)) problems += "Invalid permission: ${patch.name}"
        is ApkPatch.SetVersion -> {
          if (patch.versionCode == null && patch.versionName == null) {
            problems += "A version patch needs a version code or a version name."
          }
          if (patch.versionCode != null && patch.versionCode < 1) {
            problems += "The version code must be positive."
          }
        }
        is ApkPatch.SetDebuggable -> Unit
      }
    }

    val minSdk = patches.filterIsInstance<ApkPatch.SetMinSdk>().firstOrNull()?.sdk
    val targetSdk = patches.filterIsInstance<ApkPatch.SetTargetSdk>().firstOrNull()?.sdk
    if (minSdk != null && targetSdk != null && targetSdk < minSdk) {
      problems += "The target SDK ($targetSdk) must not be lower than the min SDK ($minSdk)."
    }

    val added = patches.filterIsInstance<ApkPatch.AddPermission>().map { it.name }.toSet()
    val removed = patches.filterIsInstance<ApkPatch.RemovePermission>().map { it.name }.toSet()
    (added intersect removed).forEach { problems += "Permission both added and removed: $it" }

    return problems
  }

  private fun checkSdk(label: String, sdk: Int, problems: MutableList<String>) {
    if (sdk !in 1..MAX_SDK) problems += "$label SDK must be between 1 and $MAX_SDK, was $sdk."
  }
}
