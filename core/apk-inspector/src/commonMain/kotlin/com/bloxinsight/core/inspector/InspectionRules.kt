package com.bloxinsight.core.inspector

object InspectionRules {
    val knownAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    fun packageNameWarning(packageName: String?): String? = when {
        packageName.isNullOrBlank() -> "Package name could not be determined."
        packageName in setOf("com.roblox.client", "com.roblox.roblox_client") ->
            "Recognized Roblox inspection target; compatibility is not implied."
        else -> null
    }

    fun unsupportedAbis(found: Set<String>): Set<String> = found - knownAbis
}
