package com.bloxinsight.core.model

data class ApkInput(val displayName: String, val sourceDescription: String, val sha256: String? = null)

data class ApkInspection(
    val packageName: String?,
    val versionName: String?,
    val versionCode: Long?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val signerSha256: List<String>,
    val nativeAbis: List<String>,
    val warnings: List<String> = emptyList(),
    val fileName: String? = null,
    val fileSizeBytes: Long? = null,
    val appLabel: String? = null,
    val requestedPermissions: List<String> = emptyList(),
    /** APK ZIP paths, e.g. lib/arm64-v8a/libexample.so */
    val nativeLibraries: List<String> = emptyList()
) {
    val isLegacyRobloxInspectionTarget: Boolean
        get() = packageName in LEGACY_ROBLOX_PACKAGE_IDS

    companion object {
        val LEGACY_ROBLOX_PACKAGE_IDS = setOf(
            "com.roblox.client",
            "com.roblox.roblox_client"
        )
    }
}

data class PatchProfile(val id: String, val displayName: String, val allowedPackageNames: Set<String>, val description: String)

data class PatchPlan(val input: ApkInput, val profile: PatchProfile, val steps: List<String>, val warnings: List<String> = emptyList())

sealed interface PatchResult {
    data class Success(val outputPath: String, val sha256: String?) : PatchResult
    data class Failure(val stage: String, val message: String) : PatchResult
    data object Cancelled : PatchResult
}

data class PatchProgress(val stage: String, val percent: Int, val message: String)
