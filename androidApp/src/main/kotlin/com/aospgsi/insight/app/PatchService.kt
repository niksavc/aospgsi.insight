package com.aospgsi.insight.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.aospgsi.insight.core.apktool.Aapt2Provider
import com.aospgsi.insight.core.apktool.AndroidKeystoreSigningKey
import com.aospgsi.insight.core.apktool.ApktoolEnvironment
import com.aospgsi.insight.core.apktool.ApktoolPatcher
import com.aospgsi.insight.core.model.PatchResult
import java.io.File

/**
 * Runs the Apktool pipeline in a foreground service so the work survives the app going to the
 * background, and reports progress through [PatchNotifications] and [PatchEvents].
 */
class PatchService : Service() {
  @Volatile private var running = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val request = intent?.let(PatchRequest::fromIntent)
    if (request == null || running) {
      if (!running) stopSelf(startId)
      return START_NOT_STICKY
    }
    running = true

    PatchNotifications.ensureChannel(this)
    val first = PatchNotifications.progress(this, 0, stageText("decode"))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        PatchNotifications.NOTIFICATION_ID,
        first,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
      )
    } else {
      startForeground(PatchNotifications.NOTIFICATION_ID, first)
    }
    PatchEvents.state.value = PatchState.Running(0, "decode")

    Thread({ execute(request, startId) }, "aospgsi-patch").start()
    return START_NOT_STICKY
  }

  private fun execute(request: PatchRequest, startId: Int) {
    val source = File(request.sourcePath)
    val result =
      try {
        patch(request, source)
      } catch (e: Throwable) {
        PatchResult.Failure("prepare", e.message ?: e.javaClass.simpleName)
      } finally {
        source.delete()
      }

    when (result) {
      is PatchResult.Success -> Log.i(TAG, "result=success path=${result.outputPath}")
      is PatchResult.Failure ->
        Log.w(TAG, "result=failure stage=${result.stage} message=${result.message}")
      PatchResult.Cancelled -> Log.i(TAG, "result=cancelled")
    }
    val manager = getSystemService(android.app.NotificationManager::class.java)
    when (result) {
      is PatchResult.Success -> {
        val packageName = request.newPackage ?: request.packageName
        PatchEvents.state.value = PatchState.Done(result.outputPath, packageName)
        stopForeground(STOP_FOREGROUND_DETACH)
        manager.notify(
          PatchNotifications.NOTIFICATION_ID,
          PatchNotifications.finished(this, true, getString(R.string.notification_patch_ready)),
        )
      }
      is PatchResult.Failure -> {
        PatchEvents.state.value = PatchState.Failed(result.stage, result.message)
        stopForeground(STOP_FOREGROUND_DETACH)
        manager.notify(
          PatchNotifications.NOTIFICATION_ID,
          PatchNotifications.finished(this, false, result.message),
        )
      }
      PatchResult.Cancelled -> {
        PatchEvents.state.value = PatchState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
      }
    }
    running = false
    stopSelf(startId)
  }

  private fun patch(request: PatchRequest, source: File): PatchResult {
    val aapt2 =
      Aapt2Provider.find(this)
        ?: return PatchResult.Failure("aapt2", "aapt2 is not available in this build.")

    val outDir = File(filesDir, "patched")
    outDir.deleteRecursively()
    outDir.mkdirs()
    val output = File(outDir, request.packageName.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".apk")

    val environment =
      ApktoolEnvironment(
        workDir = File(cacheDir, "patch-work").apply { mkdirs() },
        frameworkDir = File(filesDir, "apktool-framework"),
        aaptBinary = aapt2,
        jobs = 2,
      )
    val key = AndroidKeystoreSigningKey.getOrCreate()
    val manager = getSystemService(android.app.NotificationManager::class.java)
    var lastPercent = -1
    return ApktoolPatcher(environment).patch(source, output, request.patches, key) { progress ->
      PatchEvents.state.value = PatchState.Running(progress.percent, progress.stage)
      if (progress.percent != lastPercent) {
        lastPercent = progress.percent
        Log.i(TAG, "progress=${progress.percent} stage=${progress.stage}")
        manager.notify(
          PatchNotifications.NOTIFICATION_ID,
          PatchNotifications.progress(this, progress.percent, stageText(progress.stage)),
        )
      }
    }
  }

  private fun stageText(stage: String): String = stageLabel(this, stage)

  companion object {
    /** Logcat tag; the on-device CI test reads the result from it. */
    const val TAG = "AospGsiPatch"

    fun start(context: Context, request: PatchRequest) {
      context.startForegroundService(request.toIntent(context))
    }
  }
}

/** Localized name of a pipeline stage, as reported by the patch engine. */
fun stageLabel(context: Context, stage: String): String =
  context.getString(
    when (stage) {
      "decode" -> R.string.patch_stage_decode
      "patch" -> R.string.patch_stage_patch
      "build" -> R.string.patch_stage_build
      "align" -> R.string.patch_stage_align
      "sign" -> R.string.patch_stage_sign
      "verify" -> R.string.patch_stage_verify
      "done" -> R.string.patch_stage_done
      else -> R.string.patch_stage_working
    },
  )
