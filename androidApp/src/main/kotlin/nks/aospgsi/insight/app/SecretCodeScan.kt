package nks.aospgsi.insight.app

import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import nks.aospgsi.insight.core.apkinspector.SecretCode
import nks.aospgsi.insight.core.apkinspector.SecretCodes

/** Secret codes the app with this base APK declares; a manifest that cannot be read has none. */
internal fun readSecretCodes(baseApk: File): List<SecretCode> =
  try {
    ZipFile(baseApk).use { zip ->
      val entry = zip.getEntry("AndroidManifest.xml") ?: return emptyList()
      SecretCodes.fromManifest(zip.getInputStream(entry).use { it.readBytes() })
    }
  } catch (_: IOException) {
    emptyList()
  } catch (_: SecurityException) {
    emptyList()
  }

/** An installed app and the secret codes it declares. */
internal data class AppCodes(
  val label: String,
  val packageName: String,
  val system: Boolean,
  val codes: List<SecretCode>,
)

private const val PARALLEL_READS = 6

/**
 * Reads the manifest of every installed app's base APK and returns the apps that declare secret
 * codes, by name. [onProgress] gets (done, total) from worker threads.
 */
internal suspend fun scanInstalledApps(
  context: Context,
  onProgress: (done: Int, total: Int) -> Unit,
): List<AppCodes> = coroutineScope {
  val pm = context.packageManager
  val apps = pm.getInstalledApplications(0)
  val done = AtomicInteger()
  val gate = Semaphore(PARALLEL_READS)
  apps
    .map { app ->
      async(Dispatchers.IO) {
        val found = gate.withPermit {
          val source = app.sourceDir
          if (source == null) emptyList() else readSecretCodes(File(source))
        }
        onProgress(done.incrementAndGet(), apps.size)
        if (found.isEmpty()) {
          null
        } else {
          AppCodes(
            label = app.loadLabel(pm).toString(),
            packageName = app.packageName,
            system = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            codes = found,
          )
        }
      }
    }
    .awaitAll()
    .filterNotNull()
    .sortedBy { it.label.lowercase() }
}
