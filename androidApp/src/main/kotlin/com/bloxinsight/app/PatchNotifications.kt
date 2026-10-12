package com.bloxinsight.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Notifications of the patch job. While it runs this is an ongoing progress notification; on
 * Android 16+ it asks to be promoted to a Live Update (progress-style, shown in the status bar chip
 * and on the lock screen). Older versions get a plain progress notification.
 */
object PatchNotifications {
  const val CHANNEL_ID = "patch-progress"
  const val NOTIFICATION_ID = 1001

  /** First Android release with Live Updates (Notification.ProgressStyle, promoted ongoing). */
  private const val LIVE_UPDATES_SDK = 36

  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java)
    // Low importance: progress updates must not make a sound for every step.
    val channel =
      NotificationChannel(
        CHANNEL_ID,
        context.getString(R.string.notification_channel_patch),
        NotificationManager.IMPORTANCE_LOW,
      )
    manager.createNotificationChannel(channel)
  }

  fun progress(context: Context, percent: Int, stageText: String): Notification {
    val builder =
      baseBuilder(context)
        .setContentTitle(context.getString(R.string.notification_patch_title))
        .setContentText(stageText)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
    if (Build.VERSION.SDK_INT >= LIVE_UPDATES_SDK) {
      val style =
        Notification.ProgressStyle()
          .setStyledByProgress(true)
          .setProgressSegments(listOf(Notification.ProgressStyle.Segment(100)))
          .setProgress(percent)
      builder.setStyle(style).setShortCriticalText("$percent%").setRequestPromotedOngoing(true)
    } else {
      builder.setProgress(100, percent, false)
    }
    return builder.build()
  }

  fun finished(context: Context, success: Boolean, detail: String): Notification =
    baseBuilder(context)
      .setContentTitle(
        context.getString(
          if (success) R.string.notification_patch_done else R.string.notification_patch_failed,
        ),
      )
      .setContentText(detail)
      .setAutoCancel(true)
      .setOngoing(false)
      .build()

  @Suppress("DEPRECATION")
  private fun baseBuilder(context: Context): Notification.Builder {
    val builder =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(context, CHANNEL_ID)
      } else {
        Notification.Builder(context).setPriority(Notification.PRIORITY_LOW)
      }
    val open =
      PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    return builder.setSmallIcon(R.drawable.ic_notification).setContentIntent(open)
  }
}
