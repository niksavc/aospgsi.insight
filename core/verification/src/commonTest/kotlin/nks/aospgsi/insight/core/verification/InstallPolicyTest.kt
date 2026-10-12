package nks.aospgsi.insight.core.verification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import nks.aospgsi.insight.core.model.ApkInspection
import nks.aospgsi.insight.core.model.NativeLibraryInfo

class InstallPolicyTest {
  private val device =
    DeviceInfo(sdk = 34, supportedAbis = listOf("arm64-v8a", "armeabi-v7a"), pageSizeBytes = 4096)

  private fun apk(
    packageName: String? = "com.example.app",
    versionCode: Long? = 10,
    minSdk: Int? = 24,
    targetSdk: Int? = 34,
    abis: List<String> = emptyList(),
    signers: List<String> = listOf("aa"),
    verified: Boolean? = true,
    libs: List<NativeLibraryInfo> = emptyList(),
  ) =
    ApkInspection(
      packageName = packageName,
      versionName = "1",
      versionCode = versionCode,
      minSdk = minSdk,
      targetSdk = targetSdk,
      signerSha256 = signers,
      nativeAbis = abis,
      signatureVerified = verified,
      nativeLibraryDetails = libs,
    )

  private fun codes(a: InstallAssessment) = a.issues.map { it.code }

  @Test
  fun cleanApkCanBeInstalled() {
    val result = InstallPolicy.evaluate(apk(), null, device)
    assertTrue(result.canInstall)
    assertTrue(result.issues.isEmpty())
  }

  @Test
  fun invalidSignatureAndMissingPackageBlock() {
    val result = InstallPolicy.evaluate(apk(packageName = null, verified = false), null, device)
    assertFalse(result.canInstall)
    assertEquals(
      listOf(InstallIssueCode.NO_PACKAGE_NAME, InstallIssueCode.SIGNATURE_INVALID),
      codes(result),
    )
  }

  @Test
  fun unreadableSignatureStateIsNotTrusted() {
    assertFalse(InstallPolicy.evaluate(apk(verified = null), null, device).canInstall)
  }

  @Test
  fun minSdkAboveDeviceBlocks() {
    val result = InstallPolicy.evaluate(apk(minSdk = 35), null, device)
    assertEquals(listOf(InstallIssueCode.MIN_SDK_TOO_HIGH), codes(result))
    assertEquals(listOf("35", "34"), result.issues.single().args)
  }

  @Test
  fun minimumInstallableTargetSdkFollowsAndroidVersion() {
    assertEquals(null, InstallPolicy.minimumInstallableTargetSdk(33))
    assertEquals(23, InstallPolicy.minimumInstallableTargetSdk(34))
    assertEquals(24, InstallPolicy.minimumInstallableTargetSdk(35))
    assertEquals(24, InstallPolicy.minimumInstallableTargetSdk(37))
  }

  @Test
  fun oldTargetSdkBlocksNewInstallsOnAndroid14Plus() {
    val old = apk(targetSdk = 19)
    assertEquals(
      listOf(InstallIssueCode.TARGET_SDK_TOO_LOW),
      codes(InstallPolicy.evaluate(old, null, device)),
    )
    assertTrue(InstallPolicy.evaluate(old, null, device.copy(sdk = 33)).canInstall)
  }

  @Test
  fun oldTargetSdkDoesNotBlockUpdatesOfInstalledApp() {
    val installed = InstalledApp("com.example.app", 5, listOf("aa"))
    assertTrue(InstallPolicy.evaluate(apk(targetSdk = 19), installed, device).canInstall)
  }

  @Test
  fun abiMismatchBlocksButPureJavaApkPasses() {
    assertEquals(
      listOf(InstallIssueCode.NO_COMPATIBLE_ABI),
      codes(InstallPolicy.evaluate(apk(abis = listOf("x86_64")), null, device)),
    )
    assertTrue(InstallPolicy.evaluate(apk(abis = listOf("armeabi-v7a")), null, device).canInstall)
    assertTrue(InstallPolicy.evaluate(apk(abis = emptyList()), null, device).canInstall)
  }

  @Test
  fun signerMismatchAndDowngradeAgainstInstalledApp() {
    val installed = InstalledApp("com.example.app", 20, listOf("bb"))
    val result =
      InstallPolicy.evaluate(apk(versionCode = 10, signers = listOf("aa")), installed, device)
    assertEquals(
      listOf(InstallIssueCode.DOWNGRADE, InstallIssueCode.SIGNER_MISMATCH),
      codes(result),
    )
    assertFalse(result.canInstall)
  }

  @Test
  fun sameSignerUpgradeIsAllowed() {
    val installed = InstalledApp("com.example.app", 5, listOf("aa"))
    assertTrue(InstallPolicy.evaluate(apk(versionCode = 10), installed, device).canInstall)
  }

  @Test
  fun unknownSignerOnlyWarnsAndOnlyWhenPackageIsInTheTable() {
    val table = KnownSigners(mapOf("com.example.app" to setOf("cc")))
    val warned = InstallPolicy.evaluate(apk(signers = listOf("aa")), null, device, table)
    assertEquals(listOf(InstallIssueCode.UNKNOWN_SIGNER), codes(warned))
    assertTrue(warned.canInstall)

    val trusted = InstallPolicy.evaluate(apk(signers = listOf("CC")), null, device, table)
    assertTrue(trusted.issues.isEmpty())

    val other = InstallPolicy.evaluate(apk(packageName = "other.app"), null, device, table)
    assertTrue(other.issues.isEmpty())
  }

  @Test
  fun sixteenKbWarningOnlyOnSixteenKbDevices() {
    val bad = NativeLibraryInfo("lib/arm64-v8a/libx.so", "arm64-v8a", true, false, null, false)
    val apk = apk(abis = listOf("arm64-v8a"), libs = listOf(bad))
    assertTrue(InstallPolicy.evaluate(apk, null, device).issues.isEmpty())
    val onSixteen = InstallPolicy.evaluate(apk, null, device.copy(pageSizeBytes = 16384))
    assertEquals(listOf(InstallIssueCode.SIXTEEN_KB_INCOMPATIBLE), codes(onSixteen))
    assertTrue(onSixteen.canInstall)
  }
}
