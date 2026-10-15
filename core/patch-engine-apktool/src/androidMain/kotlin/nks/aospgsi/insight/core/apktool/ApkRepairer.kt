package nks.aospgsi.insight.core.apktool

import java.io.File
import java.util.zip.ZipException
import nks.aospgsi.insight.core.model.PatchProgress
import nks.aospgsi.insight.core.model.PatchResult

/**
 * Repairs an APK whose ZIP layout or signature was broken by a tool that rewrites archives (an
 * archiver app that saved the APK, for example). Nothing in the APK's content changes: the archive
 * is rewritten with the stored/aligned layout Android requires, the stale v1 signature files are
 * dropped, and the APK is signed again with [SigningKey] and verified. Neither decoding nor aapt2
 * is needed. Blocking; call from a background dispatcher.
 *
 * A truncated or otherwise unreadable archive cannot be repaired and is reported as such.
 */
class ApkRepairer(private val workDir: File) {
  fun repair(
    input: File,
    output: File,
    key: SigningKey,
    progress: (PatchProgress) -> Unit = {},
  ): PatchResult {
    val work = File(workDir, "repair-${System.nanoTime()}")
    var stage = "align"
    try {
      check(work.mkdirs()) { "Could not create the work directory." }

      progress(PatchProgress(stage, 10, "Aligning"))
      val aligned = File(work, "aligned.apk")
      try {
        ZipAligner.align(input, aligned, skip = ZipAligner::isJarSignatureFile)
      } catch (e: ZipException) {
        return PatchResult.Failure(
          stage,
          "The file is not a readable ZIP archive (${e.message}); it is truncated or damaged " +
            "beyond repair. Download or copy it again.",
        )
      }

      stage = "sign"
      progress(PatchProgress(stage, 60, "Signing"))
      output.absoluteFile.parentFile?.mkdirs()
      ApkSigning.sign(aligned, output, key, minSdk = null)

      stage = "verify"
      progress(PatchProgress(stage, 90, "Verifying"))
      if (!ApkSigning.verify(output)) {
        output.delete()
        return PatchResult.Failure(stage, "The signed APK did not pass signature verification.")
      }

      progress(PatchProgress("done", 100, "Done"))
      return PatchResult.Success(output.absolutePath, ApkSigning.sha256(output))
    } catch (e: OutOfMemoryError) {
      return PatchResult.Failure(stage, "Out of memory while repairing this APK.")
    } catch (e: Exception) {
      return PatchResult.Failure(stage, ApkSigning.describe(e))
    } catch (e: LinkageError) {
      return PatchResult.Failure(stage, ApkSigning.describe(e))
    } finally {
      work.deleteRecursively()
    }
  }
}
