package com.aospgsi.insight.core.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Installs an APK, or every APK inside a bundle (.apks/.xapk/.apkm), through a PackageInstaller
 * session. Rootless: Android shows its own confirmation dialog. Call from a background dispatcher.
 */
class PackageInstallerClient(private val context: Context) {
  /**
   * Streams [uri] into a new session and commits it. The file is hashed while it is written and the
   * session is abandoned if the hash differs from [expectedSha256] (the hash shown to the user at
   * inspection time), so a file swapped after the check is never installed.
   *
   * @param bundleApks entry names to install from a bundle; empty when [uri] is a plain APK.
   */
  fun install(uri: Uri, expectedSha256: String, packageName: String?, bundleApks: List<String>) {
    val installer = context.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
    params.setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)
    if (packageName != null) params.setAppPackageName(packageName)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      // Only takes effect when this app is the installer of record for an update.
      params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
    }

    val sessionId = installer.createSession(params)
    val session = installer.openSession(sessionId)
    try {
      val digest = MessageDigest.getInstance("SHA-256")
      val raw =
        context.contentResolver.openInputStream(uri)
          ?: throw IOException("Could not open the selected file.")
      DigestInputStream(raw, digest).use { input ->
        if (bundleApks.isEmpty()) {
          session.openWrite("base.apk", 0, -1).use { out -> input.copyAndSync(out, session) }
        } else {
          writeBundle(input, bundleApks.toSet(), session)
        }
        // A zip reader may stop before the end of the file; hash the rest as well.
        val sink = ByteArray(64 * 1024)
        while (input.read(sink) >= 0) {
          // Only the digest matters here.
        }
      }
      val actual = digest.digest().joinToString("") { "%02x".format(it) }
      if (!actual.equals(expectedSha256, ignoreCase = true)) {
        throw IOException("The file changed after it was inspected. Inspect it again.")
      }

      val flags =
        PendingIntent.FLAG_UPDATE_CURRENT or
          // The installer fills in extras, so the PendingIntent must be mutable.
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
      val callback =
        PendingIntent.getBroadcast(
          context,
          sessionId,
          Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName),
          flags,
        )
      session.commit(callback.intentSender)
    } catch (e: Exception) {
      session.abandon()
      throw e
    } finally {
      session.close()
    }
  }

  private fun writeBundle(
    input: InputStream,
    wanted: Set<String>,
    session: PackageInstaller.Session,
  ) {
    val zip = ZipInputStream(input)
    var index = 0
    var written = 0
    while (true) {
      val entry = zip.nextEntry ?: break
      if (entry.isDirectory || entry.name !in wanted) continue
      session.openWrite("split_${index++}.apk", 0, -1).use { out ->
        zip.copyAndSync(out, session)
      }
      written++
    }
    if (written == 0) throw IOException("The bundle contains no APK files.")
  }

  private fun InputStream.copyAndSync(out: OutputStream, session: PackageInstaller.Session) {
    copyTo(out)
    session.fsync(out)
  }
}
