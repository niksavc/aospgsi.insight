package com.bloxinsight.core.verification

import com.bloxinsight.core.model.ApkInspection

/** What the current device looks like, as far as installing an APK is concerned. */
data class DeviceInfo(
  val sdk: Int,
  /** Build.SUPPORTED_ABIS, most preferred first. */
  val supportedAbis: List<String>,
  /** Memory page size in bytes (4096 or 16384). */
  val pageSizeBytes: Int,
)

/** An app that is already installed under the same package name. */
data class InstalledApp(
  val packageName: String,
  val versionCode: Long,
  /** SHA-256 (lowercase hex) of the signing certificates. */
  val signerSha256: List<String>,
)

/**
 * Trusted signing certificates per package name. Empty by default: a signer is only "known" when
 * the user or a bundled profile says so. Hashes are never guessed.
 */
data class KnownSigners(val trusted: Map<String, Set<String>> = emptyMap()) {
  fun isKnown(packageName: String): Boolean = trusted.containsKey(packageName)

  fun isTrusted(packageName: String, signerSha256: Collection<String>): Boolean {
    val expected = trusted[packageName] ?: return false
    return signerSha256.any { it.lowercase() in expected }
  }

  companion object {
    val NONE = KnownSigners()
  }
}

enum class InstallIssueCode(val blocking: Boolean) {
  /** The manifest has no package name. */
  NO_PACKAGE_NAME(true),
  /** apksig could not verify a signature; Android refuses unsigned or broken APKs. */
  SIGNATURE_INVALID(true),
  /** args: [package name]. An installed app has a different signer, so an update fails. */
  SIGNER_MISMATCH(true),
  /** args: [installed version code, new version code]. */
  DOWNGRADE(true),
  /** args: [required API, device API]. */
  MIN_SDK_TOO_HIGH(true),
  /** args: [target SDK, minimum target SDK the device accepts]. */
  TARGET_SDK_TOO_LOW(true),
  /** args: [APK ABIs, device ABIs]. */
  NO_COMPATIBLE_ABI(true),
  /** args: [package name]. The package is in the trusted table but the signer is not. */
  UNKNOWN_SIGNER(false),
  /** The device uses 16 KiB pages and some 64-bit libraries are not 16 KiB compatible. */
  SIXTEEN_KB_INCOMPATIBLE(false),
}

data class InstallIssue(val code: InstallIssueCode, val args: List<String> = emptyList()) {
  val blocking: Boolean
    get() = code.blocking
}

data class InstallAssessment(val issues: List<InstallIssue>) {
  val canInstall: Boolean
    get() = issues.none { it.blocking }
}

/**
 * Decides, before anything is written to the system installer, whether an APK is expected to
 * install on this device. Mirrors the checks Android itself would fail on, so the user learns why
 * in plain words instead of from an opaque INSTALL_FAILED_* code.
 */
object InstallPolicy {
  /**
   * Lowest targetSdkVersion Android accepts for a new install: 23 from Android 14, 24 from Android
   * 15 onwards (verified for Android 14–17). Null when the device does not enforce a minimum.
   */
  fun minimumInstallableTargetSdk(deviceSdk: Int): Int? =
    when {
      deviceSdk >= 35 -> 24
      deviceSdk >= 34 -> 23
      else -> null
    }

  fun evaluate(
    apk: ApkInspection,
    installed: InstalledApp?,
    device: DeviceInfo,
    knownSigners: KnownSigners = KnownSigners.NONE,
  ): InstallAssessment {
    val issues = mutableListOf<InstallIssue>()
    val packageName = apk.packageName

    if (packageName.isNullOrBlank()) issues += InstallIssue(InstallIssueCode.NO_PACKAGE_NAME)
    if (apk.signatureVerified != true) issues += InstallIssue(InstallIssueCode.SIGNATURE_INVALID)

    apk.minSdk?.let { min ->
      if (min > device.sdk) {
        issues +=
          InstallIssue(InstallIssueCode.MIN_SDK_TOO_HIGH, listOf(min.toString(), "${device.sdk}"))
      }
    }

    val minimumTarget = minimumInstallableTargetSdk(device.sdk)
    val target = apk.targetSdk
    // A new install is blocked below the minimum; an update of an installed app is not.
    if (minimumTarget != null && target != null && target < minimumTarget && installed == null) {
      issues +=
        InstallIssue(
          InstallIssueCode.TARGET_SDK_TOO_LOW,
          listOf(target.toString(), minimumTarget.toString()),
        )
    }

    if (apk.nativeAbis.isNotEmpty() && apk.nativeAbis.none { it in device.supportedAbis }) {
      issues +=
        InstallIssue(
          InstallIssueCode.NO_COMPATIBLE_ABI,
          listOf(apk.nativeAbis.joinToString(), device.supportedAbis.joinToString()),
        )
    }

    if (installed != null) {
      val newCode = apk.versionCode
      if (newCode != null && newCode < installed.versionCode) {
        issues +=
          InstallIssue(
            InstallIssueCode.DOWNGRADE,
            listOf(installed.versionCode.toString(), newCode.toString()),
          )
      }
      val sameSigner = apk.signerSha256.any { it in installed.signerSha256 }
      if (apk.signerSha256.isNotEmpty() && !sameSigner) {
        issues += InstallIssue(InstallIssueCode.SIGNER_MISMATCH, listOf(installed.packageName))
      }
    }

    if (
      packageName != null &&
        knownSigners.isKnown(packageName) &&
        !knownSigners.isTrusted(packageName, apk.signerSha256)
    ) {
      issues += InstallIssue(InstallIssueCode.UNKNOWN_SIGNER, listOf(packageName))
    }

    if (device.pageSizeBytes >= SIXTEEN_KB && apk.sixteenKbReady == false) {
      issues += InstallIssue(InstallIssueCode.SIXTEEN_KB_INCOMPATIBLE)
    }

    return InstallAssessment(issues)
  }

  private const val SIXTEEN_KB = 16 * 1024
}
