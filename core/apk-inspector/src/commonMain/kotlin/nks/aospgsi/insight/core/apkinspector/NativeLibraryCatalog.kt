package nks.aospgsi.insight.core.apkinspector

enum class LibraryCategory {
  MESSAGING,
  MAPS,
  MACHINE_LEARNING,
  ANDROIDX,
  MEDIA,
  GAME_ENGINE,
}

/**
 * A native library this inspector recognizes by file name.
 *
 * @property adsCommon true when apps built on this component very often ship advertising SDKs. The
 *   library alone does not prove that an app shows ads; the UI words it as a hint.
 */
data class KnownLibrary(
  val name: String,
  val category: LibraryCategory,
  val adsCommon: Boolean = false,
)

/** File-name based identification of common native libraries. Advisory only. */
object NativeLibraryCatalog {
  private val rules: List<Pair<Regex, KnownLibrary>> =
    listOf(
      Regex("""libtmessages(\.\d+)?\.so""") to
        KnownLibrary("Telegram core (tgnet / MTProto)", LibraryCategory.MESSAGING),
      Regex("""libmaplibre\.so""") to KnownLibrary("MapLibre Native", LibraryCategory.MAPS),
      Regex("""liblanguage_id_l2c_jni\.so""") to
        KnownLibrary("ML Kit language identification", LibraryCategory.MACHINE_LEARNING),
      Regex("""libdatastore_shared_counter\.so""") to
        KnownLibrary("AndroidX DataStore (multi-process)", LibraryCategory.ANDROIDX),
      Regex("""libyuv\.so""") to KnownLibrary("libyuv (YUV/RGB conversion)", LibraryCategory.MEDIA),
      Regex("""libunity\.so""") to
        KnownLibrary("Unity engine", LibraryCategory.GAME_ENGINE, adsCommon = true),
      Regex("""libmono(bdwgc-2\.0)?\.so""") to
        KnownLibrary(
          "Mono runtime (Unity scripting)",
          LibraryCategory.GAME_ENGINE,
          adsCommon = true,
        ),
      Regex("""libil2cpp\.so""") to
        KnownLibrary(
          "IL2CPP runtime (Unity scripting)",
          LibraryCategory.GAME_ENGINE,
          adsCommon = true,
        ),
    )

  private val unityBootstrap =
    KnownLibrary("Unity launcher (libmain)", LibraryCategory.GAME_ENGINE, adsCommon = true)

  /**
   * @param fileName e.g. "libunity.so"
   * @param siblings every library file name in the same ABI folder: "libmain.so" is a generic name
   *   and only counts as Unity's launcher when "libunity.so" is next to it.
   */
  fun identify(fileName: String, siblings: Set<String> = emptySet()): KnownLibrary? {
    if (fileName == "libmain.so") return unityBootstrap.takeIf { "libunity.so" in siblings }
    return rules.firstOrNull { it.first.matches(fileName) }?.second
  }

  /** Recognized libraries of a whole APK, one entry per distinct file name. */
  fun identifyAll(paths: List<String>): List<Pair<String, KnownLibrary>> {
    val byAbi = paths.groupBy { it.substringBeforeLast('/') }
    return paths
      .map { it.substringAfterLast('/') to it.substringBeforeLast('/') }
      .distinctBy { it.first }
      .mapNotNull { (name, dir) ->
        val siblings = byAbi[dir].orEmpty().map { it.substringAfterLast('/') }.toSet()
        identify(name, siblings)?.let { name to it }
      }
      .sortedBy { it.first }
  }
}
