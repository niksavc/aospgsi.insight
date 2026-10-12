package com.aospgsi.insight.core.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/** Receives the PackageInstaller session result. Declare it (not exported) in the app manifest. */
class InstallResultReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (val code = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
      PackageInstaller.STATUS_PENDING_USER_ACTION -> {
        val confirm = confirmationIntent(intent)
        if (confirm != null) {
          confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          context.startActivity(confirm)
          InstallEvents.publish(InstallStatus.AwaitingConfirmation)
        } else {
          InstallEvents.publish(InstallStatus.Failure(code, null))
        }
      }
      PackageInstaller.STATUS_SUCCESS ->
        InstallEvents.publish(
          InstallStatus.Success(intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)),
        )
      else ->
        InstallEvents.publish(
          InstallStatus.Failure(code, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)),
        )
    }
  }

  @Suppress("DEPRECATION")
  private fun confirmationIntent(intent: Intent): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    } else {
      intent.getParcelableExtra(Intent.EXTRA_INTENT)
    }
}
