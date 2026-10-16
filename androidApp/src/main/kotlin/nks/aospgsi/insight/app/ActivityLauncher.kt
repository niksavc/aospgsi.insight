package nks.aospgsi.insight.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/** What happened when we tried to open an activity of another app. */
internal enum class LaunchResult {
  STARTED,
  /** Android refused: the activity is not exported, or it is protected by a permission. */
  DENIED,
  /** No such activity: it is disabled, or it is gone from the installed app. */
  NOT_FOUND,
  FAILED,
}

/**
 * Starts [className] of [packageName] with an empty explicit intent. Without root or a privileged
 * backend this only works for activities the app exports, which is the same limit any other app
 * has.
 */
internal fun launchActivity(
  context: Context,
  packageName: String,
  className: String,
): LaunchResult =
  try {
    context.startActivity(
      Intent().setClassName(packageName, className).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    LaunchResult.STARTED
  } catch (_: SecurityException) {
    LaunchResult.DENIED
  } catch (_: ActivityNotFoundException) {
    LaunchResult.NOT_FOUND
  } catch (_: RuntimeException) {
    LaunchResult.FAILED
  }
