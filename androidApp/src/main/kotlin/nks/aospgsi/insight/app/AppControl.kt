package nks.aospgsi.insight.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** How a clear-cache or force-stop request ended. */
sealed interface ControlResult {
  /** The system did it. */
  data object Done : ControlResult

  /** Shizuku is running but has not been allowed for this app; the permission was requested. */
  data object NeedsPermission : ControlResult

  /**
   * Nothing available here can do it (no Shizuku), so the app's system settings page was opened,
   * where the person can do it by hand.
   */
  data object OpenedSettings : ControlResult

  data class Failed(val message: String) : ControlResult
}

/**
 * Clears the cache of, or force-stops, another app.
 *
 * Both are privileged: CLEAR_APP_CACHE and FORCE_STOP_PACKAGES are never granted to an ordinary
 * install. The commands therefore run as the shell user through Shizuku (`pm clear --cache-only`,
 * `am force-stop`). Without Shizuku the app's system settings page is opened instead, where both
 * actions are a tap away.
 */
object AppControl {
  private const val SHIZUKU_REQUEST_CODE = 4711
  // Android package names: dot-separated identifiers. Keeps odd input out of the command line.
  private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

  suspend fun clearCache(context: Context, packageName: String): ControlResult =
    run(context, packageName, listOf("pm", "clear", "--cache-only", packageName))

  suspend fun forceStop(context: Context, packageName: String): ControlResult {
    val result = run(context, packageName, listOf("am", "force-stop", packageName))
    if (result == ControlResult.OpenedSettings) {
      // Without Shizuku at least the background processes can be killed.
      (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
        ?.killBackgroundProcesses(packageName)
    }
    return result
  }

  private suspend fun run(
    context: Context,
    packageName: String,
    command: List<String>,
  ): ControlResult {
    if (!PACKAGE_NAME.matches(packageName)) return ControlResult.Failed("Invalid package name")
    if (!shizukuRunning()) {
      openAppSettings(context, packageName)
      return ControlResult.OpenedSettings
    }
    if (!shizukuGranted()) {
      return try {
        Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        ControlResult.NeedsPermission
      } catch (e: Exception) {
        ControlResult.Failed(e.message ?: e.javaClass.simpleName)
      }
    }
    return withContext(Dispatchers.IO) { runAsShell(command) }
  }

  private fun shizukuRunning(): Boolean =
    try {
      Shizuku.pingBinder() && !Shizuku.isPreV11()
    } catch (_: Throwable) {
      false
    }

  private fun shizukuGranted(): Boolean =
    try {
      Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
      false
    }

  /**
   * Shizuku keeps `newProcess` private since version 13 (it prefers user services); it is still the
   * shortest way to run one command as the shell user.
   */
  private fun runAsShell(command: List<String>): ControlResult =
    try {
      val newProcess =
        Shizuku::class
          .java
          .getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
          )
          .apply { isAccessible = true }
      val process = newProcess.invoke(null, command.toTypedArray(), null, null) as Process
      val output = process.inputStream.bufferedReader().readText()
      val errors = process.errorStream.bufferedReader().readText()
      val exit = process.waitFor()
      if (exit == 0) {
        ControlResult.Done
      } else {
        ControlResult.Failed((errors.ifBlank { output }).trim().ifEmpty { "exit code $exit" })
      }
    } catch (e: Throwable) {
      ControlResult.Failed(e.message ?: e.javaClass.simpleName)
    }

  private fun openAppSettings(context: Context, packageName: String) {
    val intent =
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
      context.startActivity(intent)
    } catch (_: Exception) {}
  }
}
