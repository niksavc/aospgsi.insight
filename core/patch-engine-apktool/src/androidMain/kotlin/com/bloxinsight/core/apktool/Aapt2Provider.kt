package com.bloxinsight.core.apktool

import android.content.Context
import java.io.File

/**
 * Locates an aapt2 binary built for Android. Apktool's own bundled aapt2 only runs on desktop
 * systems, so on a phone the app must ship one itself, as jniLibs/<abi>/libaapt2.so with
 * packaging.jniLibs.useLegacyPackaging = true so that it is extracted and can be executed.
 */
object Aapt2Provider {
  const val LIBRARY_NAME = "libaapt2.so"

  /** Returns the executable aapt2, or null when this build does not ship one. */
  fun find(context: Context): File? {
    val file = File(context.applicationInfo.nativeLibraryDir, LIBRARY_NAME)
    return file.takeIf { it.isFile && it.canExecute() }
  }
}
