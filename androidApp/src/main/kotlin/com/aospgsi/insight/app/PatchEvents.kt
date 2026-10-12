package com.aospgsi.insight.app

import kotlinx.coroutines.flow.MutableStateFlow

/** Progress and outcome of the patch job, shared between the foreground service and the UI. */
sealed interface PatchState {
  data object Idle : PatchState

  data class Running(val percent: Int, val stage: String) : PatchState

  /** [path] is the signed APK; [packageName] is its package ID after patching. */
  data class Done(val path: String, val packageName: String) : PatchState

  data class Failed(val stage: String, val message: String?) : PatchState
}

object PatchEvents {
  val state = MutableStateFlow<PatchState>(PatchState.Idle)
}
