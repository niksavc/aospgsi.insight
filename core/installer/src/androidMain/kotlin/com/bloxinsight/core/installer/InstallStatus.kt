package com.bloxinsight.core.installer

/** Outcome reported by the system installer for the last install session. */
sealed interface InstallStatus {
  /** The user has to confirm in the system dialog; it was opened already. */
  data object AwaitingConfirmation : InstallStatus

  data class Success(val packageName: String?) : InstallStatus

  /** [code] is a PackageInstaller.STATUS_FAILURE* constant. */
  data class Failure(val code: Int, val message: String?) : InstallStatus
}

/** Hands install results from the manifest receiver to whoever is on screen. */
object InstallEvents {
  @Volatile var listener: ((InstallStatus) -> Unit)? = null

  internal fun publish(status: InstallStatus) {
    listener?.invoke(status)
  }
}
