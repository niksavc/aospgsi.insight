package nks.aospgsi.insight.core.apktool

import brut.androlib.ApkBuilder
import brut.androlib.ApkDecoder
import brut.androlib.Config
import brut.androlib.meta.ApkInfo
import java.io.File
import nks.aospgsi.insight.core.model.PatchProgress
import nks.aospgsi.insight.core.model.PatchResult
import nks.aospgsi.insight.core.patchapi.ApkPatch
import nks.aospgsi.insight.core.patchapi.PatchValidation

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
      ApkSigning.sign(aligned, output, key, minSdk)

      stage = "verify"
      progress(PatchProgress(stage, 92, "Verifying"))
      if (!ApkSigning.verify(output)) {
        output.delete()
        return PatchResult.Failure(stage, "The signed APK did not pass signature verification.")
      }

      progress(PatchProgress("done", 100, "Done"))
      return PatchResult.Success(output.absolutePath, ApkSigning.sha256(output))
    } catch (e: OutOfMemoryError) {
      return PatchResult.Failure(stage, "Out of memory while patching this APK.")
    } catch (e: Exception) {
      return PatchResult.Failure(stage, ApkSigning.describe(e))
    } catch (e: LinkageError) {
      return PatchResult.Failure(stage, ApkSigning.describe(e))
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
