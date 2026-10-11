package com.bloxinsight.core.apktool

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
 * Manifest-level patching pipeline: decode the manifest, apply [ApkPatch]es, rebuild with aapt2,
 * zip-align, sign and verify. Code (dex) and resources.arsc are copied through untouched, which
 * keeps this fast and avoids smali/arsc round trips. Blocking; call from a background dispatcher.
 */
class ApktoolPatcher(private val environment: ApktoolEnvironment) {
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
      val verification = ApkVerifier.Builder(output).build().verify()
      if (!verification.isVerified) {
        output.delete()
        return PatchResult.Failure(stage, "The signed APK did not pass signature verification.")
      }

      progress(PatchProgress("done", 100, "Done"))
      return PatchResult.Success(output.absolutePath, sha256(output))
    } catch (e: OutOfMemoryError) {
      return PatchResult.Failure(stage, "Out of memory while patching this APK.")
    } catch (e: Exception) {
      return PatchResult.Failure(stage, e.message ?: e.javaClass.simpleName)
    } finally {
      work.deleteRecursively()
    }
  }

  private fun config(decoding: Boolean): Config =
    Config(APKTOOL_VERSION).apply {
      setForced(true)
      setJobs(environment.jobs)
      setFrameworkDirectory(environment.frameworkDir.path)
      environment.aaptBinary?.let { setAaptBinary(it.path) }
      if (decoding) {
        // Keep dex and resources.arsc as raw files; only the manifest is decoded.
        setDecodeSources(Config.DecodeSources.NONE)
        setDecodeResources(Config.DecodeResources.ONLY_MANIFEST)
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
    /** Keep in sync with the apktool entry in gradle/libs.versions.toml. */
    const val APKTOOL_VERSION = "3.0.3"
  }
}
