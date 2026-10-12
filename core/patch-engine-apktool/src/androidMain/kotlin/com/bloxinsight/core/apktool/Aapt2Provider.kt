package com.bloxinsight.core.apktool

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Locates the aapt2 binary built from AOSP sources (scripts/build-aapt2.sh). Apktool's own bundled
 * aapt2 only runs on desktop systems, so the app ships this one as jniLibs/<abi>/libaapt2.so with
 * packaging.jniLibs.useLegacyPackaging = true so that it is extracted and can be executed.
 */
object Aapt2Provider {
  const val LIBRARY_NAME = "libaapt2.so"

  /** The binary is built for Android 7.0 (API 24), the app's minSdk. */
  const val MIN_SDK = 24

  fun isSupported(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk >= MIN_SDK

  /**
   * Returns the executable aapt2, or null when this Android version is too old or this build does
   * not ship one (local builds without scripts/build-aapt2.sh output).
   */
  fun find(context: Context): File? {
    if (!isSupported()) return null
    val file = File(context.applicationInfo.nativeLibraryDir, LIBRARY_NAME)
    return file.takeIf { it.isFile && it.canExecute() }
  }
}
