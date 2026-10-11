package com.bloxinsight.integrations

enum class BackendCapability {
    INSPECT_PACKAGES,
    INSTALL_PACKAGE,
    BACKUP_PACKAGE,
    PRIVILEGED_FILE_OPERATION
}

data class BackendStatus(val available: Boolean, val authorized: Boolean, val message: String)

interface ExecutionBackend {
    val id: String
    suspend fun getCapabilities(): Set<BackendCapability>
    suspend fun checkAvailability(): BackendStatus
}

object BackendIds {
    const val ANDROID = "android"
    const val SHIZUKU = "shizuku"
    const val DHIZUKU = "dhizuku"
    const val KERNELSU = "kernelsu"
    const val MAGISK = "magisk"
    const val APATCH = "apatch"
}
