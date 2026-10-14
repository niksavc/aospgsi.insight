package nks.aospgsi.insight.core.apkinspector

/** How an APK's native code relates to 32-bit and 64-bit ABIs. */
enum class AbiSupport {
  /** No native libraries: the app is pure Java/Kotlin and runs on any ABI. */
  NO_NATIVE,
  ONLY_32_BIT,
  ONLY_64_BIT,
  BOTH,
}

object AbiRules {
  fun support(abis: Collection<String>): AbiSupport {
    if (abis.isEmpty()) return AbiSupport.NO_NATIVE
    val has64 = abis.any { InspectionRules.is64BitAbi(it) }
    val has32 = abis.any { !InspectionRules.is64BitAbi(it) }
    return when {
      has64 && has32 -> AbiSupport.BOTH
      has64 -> AbiSupport.ONLY_64_BIT
      else -> AbiSupport.ONLY_32_BIT
    }
  }

  /**
   * Whether the app's native libraries can load on a device whose ABIs are [deviceAbis] (as in
   * Build.SUPPORTED_ABIS, which already includes ABIs translated by the system). Null when the app
   * has no native code, or the device list is unknown.
   */
  fun runsOnDevice(appAbis: Collection<String>, deviceAbis: Collection<String>): Boolean? {
    if (appAbis.isEmpty() || deviceAbis.isEmpty()) return null
    return appAbis.any { it in deviceAbis }
  }

  /** A warning when the native libraries cannot load on this device, else null. */
  fun deviceWarning(appAbis: Collection<String>, deviceAbis: Collection<String>): String? =
    if (runsOnDevice(appAbis, deviceAbis) == false) {
      "Native libraries are built for ${appAbis.sorted().joinToString()} only, but this device " +
        "supports ${deviceAbis.joinToString()}; the app is likely to crash or fail to start."
    } else {
      null
    }
}
