package com.bloxinsight.core.apkinspector

import com.bloxinsight.core.model.ApkInspection

object InspectionRules {
    val knownAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    fun packageNameWarning(packageName: String?): String? = when {
        packageName.isNullOrBlank() -> "Package name could not be determined."
        packageName in ApkInspection.LEGACY_ROBLOX_PACKAGE_IDS ->
            "Recognized Roblox inspection target; compatibility is not implied."
        else -> null
    }

    fun unsupportedAbis(found: Set<String>): Set<String> = found - knownAbis

    /** Extracts sorted, distinct ABI directory names from APK ZIP paths like `lib/arm64-v8a/libx.so`. */
    fun abisFromNativeLibraries(paths: List<String>): List<String> = paths.mapNotNull { path ->
        val parts = path.split('/')
        if (parts.size >= 3 && parts[0] == "lib") parts[1] else null
    }.distinct().sorted()
}
