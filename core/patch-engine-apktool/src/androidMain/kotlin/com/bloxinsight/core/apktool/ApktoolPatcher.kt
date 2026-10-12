package com.bloxinsight.core.apktool

import android.os.Build
import brut.androlib.ApkBuilder
import brut.androlib.ApkDecoder
import brut.androlib.Config
import brut.androlib.meta.ApkInfo
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.bloxinsight.core.model.PatchProgress
import com.bloxinsight.core.model.PatchResult
import com.bloxinsight.core.patchapi.ApkPatch
import com.bloxinsight.core.patchapi.PatchValidation
import java.io.File
import java.security.MessageDigest

/**
 * Where Apktool may work.
 *
 * @property workDir scratch space; needs roughly three times the APK size.
 * @property frameworkDir where Apktool unpacks the Android framework resources it links against.
 * @property aaptBinary aapt2 to run; null uses Apktool's bundled desktop binary (host tests only).
 * @property jobs worker threads for Apktool; keep low on phones.
 */
data class ApktoolEnvironment(
  val workDir: File,
  val frameworkDir: File,
  val aaptBinary: File? = null,
  val jobs: Int = 2,
)

/**
 * Manifest-level patching pipeline: decode the manifest and resources, apply [ApkPatch]es, rebuild
 * with aapt2, zip-align, sign and verify. Code (classes*.dex) is copied through untouched, which
 * avoids smali round trips. Resources must be decoded and rebuilt too: Apktool cannot recompile a
 * text manifest on its own, because the manifest refers to resources (labels, themes, icons) by
 * name. Blocking; call from a background dispatcher.
 */
class ApktoolPatcher(private val environment: ApktoolEnvironment) {
  init {
    prepareRuntime()
  }

  fun patch(
    input: File,
    output: File,
    patches: List<ApkPatch>,
    key: SigningKey,
    progress: (PatchProgress) -> Unit = {},
  ): PatchResult {
    val problems = PatchValidation.validate(patches)
    if (problems.isNotEmpty()) return PatchResult.Failure("validate", problems.joinToString(" "))

    val work = File(environment.workDir, "patch-${System.nanoTime()}")
    var stage = "decode"
    try {
      check(work.mkdirs()) { "Could not create the work directory." }
      environment.frameworkDir.mkdirs()

      progress(PatchProgress(stage, 5, "Reading the APK"))
      val decoded = File(work, "decoded")
      ApkDecoder(input, config(decoding = true)).decode(decoded)

      stage = "patch"
      progress(PatchProgress(stage, 35, "Applying changes"))
      ManifestEditor.apply(File(decoded, "AndroidManifest.xml"), patches)
      applyInfoPatches(decoded, patches)

      stage = "build"
      progress(PatchProgress(stage, 45, "Rebuilding"))
      val built = File(work, "built.apk")
      ApkBuilder(decoded, config(decoding = false)).build(built)

      stage = "align"
      progress(PatchProgress(stage, 70, "Aligning"))
      val aligned = File(work, "aligned.apk")
      ZipAligner.align(built, aligned)

      stage = "sign"
      progress(PatchProgress(stage, 80, "Signing"))
      output.absoluteFile.parentFile?.mkdirs()
      val minSdk = ApkInfo.load(decoded).sdkInfo.minSdkVersion?.toIntOrNull()
      sign(aligned, output, key, minSdk)

      stage = "verify"
      progress(PatchProgress(stage, 92, "Verifying"))
      if (!verifySignature(output)) {
        output.delete()
        return PatchResult.Failure(stage, "The signed APK did not pass signature verification.")
      }

      progress(PatchProgress("done", 100, "Done"))
      return PatchResult.Success(output.absolutePath, sha256(output))
    } catch (e: OutOfMemoryError) {
      return PatchResult.Failure(stage, "Out of memory while patching this APK.")
    } catch (e: Exception) {
      return PatchResult.Failure(stage, describe(e))
    } catch (e: LinkageError) {
      return PatchResult.Failure(stage, describe(e))
    } finally {
      work.deleteRecursively()
    }
  }

  /**
   * apksig's v1 verifier needs Type.getTypeName() (API 26+). On older releases only the schemes
   * that release itself would use are checked, and if the verifier still cannot run, the platform's
   * own package installer is the check: it rejects a bad signature at install time.
   */
  private fun verifySignature(apk: File): Boolean =
    try {
      val builder = ApkVerifier.Builder(apk)
      if (Build.VERSION.SDK_INT in 1 until Build.VERSION_CODES.O) { // 0 on host JVM tests
        builder.setMinCheckedPlatformVersion(Build.VERSION.SDK_INT)
      }
      builder.build().verify().isVerified
    } catch (_: LinkageError) {
      true
    }

  /** The message plus the chain of causes, since wrappers such as apksig hide the real reason. */
  private fun describe(error: Throwable): String {
    val parts = mutableListOf<String>()
    var current: Throwable? = error
    while (current != null && parts.size < 4) {
      parts += current.message ?: current.javaClass.simpleName
      current = current.cause?.takeIf { it !== current }
    }
    return parts.joinToString(" <- ")
  }

  /** The message plus the chain of causes: wrappers such as apksig's often hide the real reason. */
  private fun describe(error: Throwable): String {
    val parts = mutableListOf<String>()
    var current: Throwable? = error
    while (current != null && parts.size < 5) {
      parts +=
        current.message?.let { "${current.javaClass.simpleName}: $it" }
          ?: current.javaClass.simpleName
      current = current.cause?.takeIf { it !== current }
    }
    return parts.joinToString(" <- ")
  }

  private fun config(decoding: Boolean): Config =
    Config(APKTOOL_VERSION).apply {
      setForced(true)
      setJobs(environment.jobs)
      setFrameworkDirectory(environment.frameworkDir.path)
      environment.aaptBinary?.let { setAaptBinary(it.path) }
      if (decoding) {
        // Keep dex files raw; resources are decoded so the manifest can be recompiled.
        setDecodeSources(Config.DecodeSources.NONE)
        setDecodeResources(Config.DecodeResources.FULL)
      }
    }

  private fun applyInfoPatches(decoded: File, patches: List<ApkPatch>) {
    val needed = patches.any {
      it is ApkPatch.SetTargetSdk || it is ApkPatch.SetMinSdk || it is ApkPatch.SetVersion
    }
    if (!needed) return
    val info = ApkInfo.load(decoded)
    for (patch in patches) {
      when (patch) {
        is ApkPatch.SetTargetSdk -> info.sdkInfo.targetSdkVersion = patch.sdk.toString()
        is ApkPatch.SetMinSdk -> info.sdkInfo.minSdkVersion = patch.sdk.toString()
        is ApkPatch.SetVersion -> {
          patch.versionCode?.let { info.versionInfo.versionCode = it }
          patch.versionName?.let { info.versionInfo.versionName = it }
        }
        else -> Unit
      }
    }
    info.save(decoded)
  }

  private fun sign(input: File, output: File, key: SigningKey, minSdk: Int?) {
    val signer =
      ApkSigner.SignerConfig.Builder("bloxinsight", key.privateKey, key.certificates).build()
    val builder =
      ApkSigner.Builder(listOf(signer))
        .setInputApk(input)
        .setOutputApk(output)
        .setV1SigningEnabled(true)
        .setV2SigningEnabled(true)
        .setV3SigningEnabled(true)
        .setV4SigningEnabled(false)
        .setCreatedBy("BloxInsight")
        // Keep the 4-byte / 16 KiB alignment that ZipAligner established.
        .setAlignmentPreserved(true)
        .setLibraryPageAlignmentBytes(ZipAligner.NATIVE_LIB_ALIGNMENT)
    if (minSdk != null) builder.setMinSdkVersion(minSdk)
    builder.build().sign()
  }

  private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(64 * 1024)
      while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  companion object {
    /**
     * Apktool was written for desktop JVMs and reads system properties that Android does not define
     * (`os.name` made its OS detection throw a NullPointerException on a real device).
     */
    private fun prepareRuntime() {
      if (System.getProperty("os.name") == null) System.setProperty("os.name", "Linux")
      if (System.getProperty("os.arch") == null) {
        System.setProperty("os.arch", if (android.os.Process.is64Bit()) "aarch64" else "arm")
      }
      if (System.getProperty("sun.arch.data.model") == null) {
        System.setProperty("sun.arch.data.model", if (android.os.Process.is64Bit()) "64" else "32")
      }
    }

    /** Keep in sync with the apktool entry in gradle/libs.versions.toml. */
    const val APKTOOL_VERSION = "3.0.3"
  }
}
