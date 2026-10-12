package nks.aospgsi.insight.core.installer

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.system.Os
import android.system.OsConstants
import java.security.MessageDigest
import nks.aospgsi.insight.core.verification.DeviceInfo
import nks.aospgsi.insight.core.verification.InstalledApp

/** Reads the facts about this device and about already-installed apps that the policy needs. */
class DeviceProbe(private val context: Context) {
  fun deviceInfo(): DeviceInfo =
    DeviceInfo(
      sdk = Build.VERSION.SDK_INT,
      supportedAbis = Build.SUPPORTED_ABIS.toList(),
      pageSizeBytes = pageSize(),
    )

  private fun pageSize(): Int =
    try {
      Os.sysconf(OsConstants._SC_PAGESIZE).toInt()
    } catch (_: Exception) {
      4096
    }

  /** Returns the installed app with this package name, or null when it is not installed. */
  @Suppress("DEPRECATION")
  fun installedApp(packageName: String): InstalledApp? =
    try {
      val pm = context.packageManager
      val info: PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
          pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }
      val signatures =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          info.signingInfo?.apkContentsSigners
        } else {
          info.signatures
        }
      val versionCode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          info.longVersionCode
        } else {
          info.versionCode.toLong()
        }
      InstalledApp(
        packageName = packageName,
        versionCode = versionCode,
        signerSha256 =
          signatures.orEmpty().map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b ->
              "%02x".format(b)
            }
          },
      )
    } catch (_: PackageManager.NameNotFoundException) {
      null
    }
}
