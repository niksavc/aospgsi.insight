package com.aospgsi.insight.core.apkinspector

import com.aospgsi.insight.core.model.ApkInspection
import com.aospgsi.insight.core.model.NativeLibraryInfo

object InspectionRules {
  val knownAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

  fun packageNameWarning(packageName: String?): String? =
    when {
      packageName.isNullOrBlank() -> "Package name could not be determined."
      packageName in ApkInspection.LEGACY_ROBLOX_PACKAGE_IDS ->
        "Recognized Roblox inspection target; compatibility is not implied."
      else -> null
    }

  fun unsupportedAbis(found: Set<String>): Set<String> = found - knownAbis

  /**
   * Extracts sorted, distinct ABI directory names from APK ZIP paths like `lib/arm64-v8a/libx.so`.
   */
  fun abisFromNativeLibraries(paths: List<String>): List<String> =
    paths
      .mapNotNull { path ->
        val parts = path.split('/')
        if (parts.size >= 3 && parts[0] == "lib") parts[1] else null
      }
      .distinct()
      .sorted()

  val abis64Bit = setOf("arm64-v8a", "x86_64", "riscv64")

  fun is64BitAbi(abi: String): Boolean = abi in abis64Bit

  /** Warnings about 64-bit native libraries that will not work well on 16 KiB page-size devices. */
  fun sixteenKbWarnings(libs: List<NativeLibraryInfo>): List<String> {
    val libs64 = libs.filter { it.is64Bit }
    if (libs64.isEmpty()) return emptyList()
    val warnings = mutableListOf<String>()
    val elfBad = libs64.count { it.elfAlignedTo16Kb == false }
    val elfUnknown = libs64.count { it.elfAlignedTo16Kb == null }
    val zipBad = libs64.count { it.zipAlignedTo16Kb == false }
    if (elfBad > 0) {
      warnings +=
        "$elfBad of ${libs64.size} 64-bit native libraries have ELF segments not aligned to 16 KiB."
    }
    if (elfUnknown > 0) {
      warnings += "$elfUnknown of ${libs64.size} 64-bit native libraries could not be read as ELF."
    }
    if (zipBad > 0) {
      warnings +=
        "$zipBad of ${libs64.size} 64-bit native libraries are stored at offsets not aligned to 16 KiB."
    }
    return warnings
  }
}
