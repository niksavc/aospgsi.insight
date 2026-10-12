package nks.aospgsi.insight.core.model

data class ApkInput(
  val displayName: String,
  val sourceDescription: String,
  val sha256: String? = null,
)

data class ApkInspection(
  val packageName: String?,
  val versionName: String?,
  val versionCode: Long?,
  val minSdk: Int?,
  val targetSdk: Int?,
  val signerSha256: List<String>,
  val nativeAbis: List<String>,
  val warnings: List<String> = emptyList(),
  val fileName: String? = null,
  val fileSizeBytes: Long? = null,
  val appLabel: String? = null,
  val requestedPermissions: List<String> = emptyList(),
  /** APK ZIP paths, e.g. lib/arm64-v8a/libexample.so */
  val nativeLibraries: List<String> = emptyList(),
  val nativeLibraryDetails: List<NativeLibraryInfo> = emptyList(),
  val declaredPermissions: List<String> = emptyList(),
  val activities: List<ApkComponent> = emptyList(),
  val services: List<ApkComponent> = emptyList(),
  val receivers: List<ApkComponent> = emptyList(),
  val providers: List<ApkComponent> = emptyList(),
  val metaData: Map<String, String> = emptyMap(),
  /** SHA-256 of the file the user picked (an APK or a bundle such as .apks/.xapk/.apkm). */
  val sha256: String? = null,
  /** Null when the signature could not be checked at all. */
  val signatureVerified: Boolean? = null,
  /** Signature schemes that verified, e.g. "v1", "v2", "v3". */
  val signatureSchemes: List<String> = emptyList(),
  /** APK file names inside a bundle; empty when the input was a plain APK. */
  val bundleApks: List<String> = emptyList(),
  /**
   * Whether the manifest sets android:multiarch="true" (an app exposing an API to both 32-bit and
   * 64-bit callers). Null when it could not be read.
   */
  val multiArch: Boolean? = null,
) {
  val isLegacyRobloxInspectionTarget: Boolean
    get() = packageName in LEGACY_ROBLOX_PACKAGE_IDS

  val isBundle: Boolean
    get() = bundleApks.isNotEmpty()

  /**
   * Whether all 64-bit native libraries are ready for 16 KiB page-size devices: ELF segments
   * aligned and, when stored uncompressed, zip-aligned. Null when there are no 64-bit libraries to
   * judge.
   */
  val sixteenKbReady: Boolean?
    get() {
      val libs64 = nativeLibraryDetails.filter { it.is64Bit }
      if (libs64.isEmpty()) return null
      return libs64.all { it.elfAlignedTo16Kb == true && it.zipAlignedTo16Kb != false }
    }

  companion object {
    val LEGACY_ROBLOX_PACKAGE_IDS =
      setOf(
        "com.roblox.client",
        "com.roblox.roblox_client",
      )
  }
}

data class ApkComponent(val name: String, val exported: Boolean)

data class NativeLibraryInfo(
  /** APK ZIP path, e.g. lib/arm64-v8a/libexample.so */
  val path: String,
  val abi: String,
  val is64Bit: Boolean,
  /** True when the entry is stored without compression (so its zip offset matters). */
  val storedUncompressed: Boolean,
  /** Null when the entry is compressed, where zip alignment does not apply. */
  val zipAlignedTo16Kb: Boolean?,
  /** Null when the ELF header could not be read. */
  val elfAlignedTo16Kb: Boolean?,
)

data class PatchProfile(
  val id: String,
  val displayName: String,
  val allowedPackageNames: Set<String>,
  val description: String,
)

data class PatchPlan(
  val input: ApkInput,
  val profile: PatchProfile,
  val steps: List<String>,
  val warnings: List<String> = emptyList(),
)

sealed interface PatchResult {
  data class Success(val outputPath: String, val sha256: String?) : PatchResult

  data class Failure(val stage: String, val message: String) : PatchResult

  data object Cancelled : PatchResult
}

data class PatchProgress(val stage: String, val percent: Int, val message: String)
