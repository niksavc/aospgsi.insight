package nks.aospgsi.insight.core.apkinspector

enum class LibraryCategory {
  GAME_ENGINE,
  APP_CORE,
  MAPS,
  MACHINE_LEARNING,
  MEDIA,
  ANDROIDX,
  RUNTIME,
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
