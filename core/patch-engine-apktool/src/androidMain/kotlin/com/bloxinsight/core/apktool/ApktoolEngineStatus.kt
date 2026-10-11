package com.bloxinsight.core.apktool

/**
 * Placeholder for the Android-specific Apktool adapter.
 *
 * Before implementation, select a pinned Apktool release, confirm runtime requirements,
 * review its license and transitive dependencies, and isolate file/process operations.
 */
data class ApktoolEngineStatus(
    val integrated: Boolean = false,
    val message: String =
        "Apktool adapter is not yet integrated. No decoding or rebuilding is performed."
)
