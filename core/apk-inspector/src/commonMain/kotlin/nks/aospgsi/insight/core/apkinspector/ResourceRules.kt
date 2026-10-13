package nks.aospgsi.insight.core.apkinspector

import nks.aospgsi.insight.core.model.ResourceKind

/** Tells JSON files inside an APK apart. Pure functions so they can be tested on the host. */
object ResourceRules {
  /** True for paths worth listing: .json files outside META-INF. */
  fun isJsonResource(path: String): Boolean =
    path.endsWith(".json", ignoreCase = true) && !path.startsWith("META-INF/")

  /**
   * Lottie (Bodymovin) files start with a header of short keys: version `v`, frame rate `fr`,
   * in/out points `ip`/`op`, size `w`/`h`. Looking for them in the first bytes avoids parsing large
   * files.
   */
  fun kindOf(head: String): ResourceKind {
    val keys = listOf("\"v\"", "\"fr\"", "\"ip\"", "\"op\"", "\"w\"", "\"h\"")
    val hits = keys.count { head.contains(it) }
    return if (hits >= 5 && head.contains("\"layers\"") || hits == keys.size) {
      ResourceKind.LOTTIE
    } else {
      ResourceKind.JSON
    }
  }
}
