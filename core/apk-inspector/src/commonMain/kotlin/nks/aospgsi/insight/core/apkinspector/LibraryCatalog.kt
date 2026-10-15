package nks.aospgsi.insight.core.apkinspector

enum class LibraryCategory {
  GAME_ENGINE,
  APP_CORE,
  MAPS,
  MACHINE_LEARNING,
  MEDIA,
  ANDROIDX,
  RUNTIME,
  CRASH_REPORTING,
  STORAGE,
}

/** What a well-known native library is. Matching is by file name only, so it is a hint. */
data class LibraryInfo(
  val name: String,
  val project: String,
  val license: String,
  val category: LibraryCategory,
  val proprietary: Boolean,
)

object LibraryCatalog {
  private data class Entry(val pattern: Regex, val info: LibraryInfo)

  private fun entry(
    pattern: String,
    name: String,
    project: String,
    license: String,
    category: LibraryCategory,
    proprietary: Boolean = false,
  ) = Entry(Regex(pattern), LibraryInfo(name, project, license, category, proprietary))

  private val entries =
    listOf(
      entry(
        """libtmessages\.\d+\.so""",
        "Telegram messenger core (network, crypto, MTProto)",
        "Telegram for Android",
        "GPL-2.0",
        LibraryCategory.APP_CORE,
      ),
      entry(
        """libmaplibre\.so""",
        "MapLibre Native map renderer",
        "MapLibre",
        "BSD-2-Clause",
        LibraryCategory.MAPS,
      ),
      entry(
        """liblanguage_id_l2c_jni\.so""",
        "ML Kit language identification",
        "Google ML Kit",
        "Proprietary (ML Kit terms)",
        LibraryCategory.MACHINE_LEARNING,
        proprietary = true,
      ),
      entry(
        """libdatastore_shared_counter\.so""",
        "AndroidX DataStore multi-process counter",
        "AndroidX",
        "Apache-2.0",
        LibraryCategory.ANDROIDX,
      ),
      entry(
        """libyuv\.so""",
        "libyuv image conversion",
        "libyuv",
        "BSD-3-Clause",
        LibraryCategory.MEDIA,
      ),
      entry(
        """libunity\.so""",
        "Unity Player",
        "Unity Technologies",
        "Proprietary",
        LibraryCategory.GAME_ENGINE,
        proprietary = true,
      ),
      entry(
        """libmono(sgen-2\.0)?\.so""",
        "Mono runtime (runs the game's C# code)",
        "Mono / Unity",
        "MIT",
        LibraryCategory.RUNTIME,
      ),
      entry(
        """libil2cpp\.so""",
        "Unity IL2CPP runtime (C# compiled to native code)",
        "Unity Technologies",
        "Proprietary",
        LibraryCategory.RUNTIME,
        proprietary = true,
      ),
      entry(
        """libmain\.so""",
        "Native entry point (Unity's loader when libunity.so is present)",
        "Unity Technologies",
        "Proprietary",
        LibraryCategory.GAME_ENGINE,
        proprietary = true,
      ),
      entry(
        """libflutter\.so""",
        "Flutter engine",
        "Flutter",
        "BSD-3-Clause",
        LibraryCategory.GAME_ENGINE,
      ),
      entry(
        """libreactnativejni\.so""",
        "React Native runtime",
        "Meta",
        "MIT",
        LibraryCategory.RUNTIME,
      ),
      entry(
        """libsentry(-android)?\.so""",
        "Sentry native crash handler",
        "Sentry",
        "MIT",
        LibraryCategory.CRASH_REPORTING,
      ),
      entry(
        """libcrashlytics[\w-]*\.so""",
        "Firebase Crashlytics native crash reporter",
        "Google Firebase",
        "Proprietary (Firebase terms)",
        LibraryCategory.CRASH_REPORTING,
        proprietary = true,
      ),
      entry(
        """libhermes\.so""",
        "Hermes JavaScript engine (React Native)",
        "Meta",
        "MIT",
        LibraryCategory.RUNTIME,
      ),
      entry(
        """libjsc\.so""",
        "JavaScriptCore engine (React Native)",
        "WebKit",
        "LGPL-2.1",
        LibraryCategory.RUNTIME,
      ),
      entry(
        """libgodot_android\.so""",
        "Godot engine",
        "Godot",
        "MIT",
        LibraryCategory.GAME_ENGINE,
      ),
      entry(
        """libcocos(2dcpp)?\.so""",
        "Cocos2d-x engine",
        "Cocos",
        "MIT",
        LibraryCategory.GAME_ENGINE,
      ),
      entry(
        """libgdx\.so""",
        "libGDX native helpers",
        "libGDX",
        "Apache-2.0",
        LibraryCategory.GAME_ENGINE,
      ),
      entry("""libSDL2\.so""", "SDL 2 library", "SDL", "Zlib", LibraryCategory.GAME_ENGINE),
      entry(
        """libfmod(L)?\.so""",
        "FMOD audio engine",
        "Firelight Technologies",
        "Proprietary",
        LibraryCategory.MEDIA,
        proprietary = true,
      ),
      entry(
        """libopenal\.so""",
        "OpenAL Soft audio",
        "OpenAL Soft",
        "LGPL-2.0",
        LibraryCategory.MEDIA,
      ),
      entry(
        """libav(codec|format|util)[\w.-]*\.so""",
        "FFmpeg media library",
        "FFmpeg",
        "LGPL-2.1 / GPL-2.0 depending on build",
        LibraryCategory.MEDIA,
      ),
      entry("""libvlc\.so""", "VLC media engine", "VideoLAN", "LGPL-2.1", LibraryCategory.MEDIA),
      entry(
        """libwebp[\w]*\.so""",
        "WebP image codec",
        "WebM Project",
        "BSD-3-Clause",
        LibraryCategory.MEDIA,
      ),
      entry(
        """libsqlcipher\.so""",
        "SQLCipher encrypted database",
        "Zetetic",
        "BSD-3-Clause",
        LibraryCategory.STORAGE,
      ),
      entry(
        """libmmkv\.so""",
        "MMKV key-value store",
        "Tencent",
        "BSD-3-Clause",
        LibraryCategory.STORAGE,
      ),
      entry(
        """libjnidispatch\.so""",
        "JNA native bridge",
        "Java Native Access",
        "LGPL-2.1 or Apache-2.0",
        LibraryCategory.RUNTIME,
      ),
      entry(
        """libc\+\+_shared\.so""",
        "LLVM C++ standard library",
        "LLVM",
        "Apache-2.0 with LLVM exception",
        LibraryCategory.RUNTIME,
      ),
    )

  /** Identifies a library from its path or file name, or null when it is not in the catalog. */
  fun identify(path: String): LibraryInfo? {
    val fileName = path.substringAfterLast('/')
    return entries.firstOrNull { it.pattern.matches(fileName) }?.info
  }

  /** The game engine or framework that these libraries add up to, if recognizable. */
  fun detectFramework(paths: Collection<String>): String? {
    val names = paths.map { it.substringAfterLast('/') }.toSet()
    return when {
      "libunity.so" in names -> if ("libil2cpp.so" in names) "Unity (IL2CPP)" else "Unity (Mono)"
      "libflutter.so" in names -> "Flutter"
      "libreactnativejni.so" in names -> "React Native"
      else -> null
    }
  }
}
