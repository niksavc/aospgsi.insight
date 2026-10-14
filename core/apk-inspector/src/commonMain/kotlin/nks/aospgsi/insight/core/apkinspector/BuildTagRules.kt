package nks.aospgsi.insight.core.apkinspector

/**
 * Recognizes languages and build tooling from names of entries in the APK archive, for the badges
 * in the inspector header. A tag shows the file is present, not how much of the app uses it.
 */
object BuildTagRules {
  fun fromEntryNames(names: Sequence<String>): List<String> {
    var kotlin = false
    var gradle = false
    var compose = false
    var java = false
    for (name in names) {
      when {
        name.endsWith(".kotlin_module") ||
          name.startsWith("kotlin/") ||
          name == "kotlin-tooling-metadata.json" -> kotlin = true
        name == "META-INF/com/android/build/gradle/app-metadata.properties" -> gradle = true
        name.startsWith("META-INF/androidx.compose") && name.endsWith(".version") -> compose = true
        name.endsWith(".dex") && name.indexOf('/') < 0 -> java = true
      }
    }
    return buildList {
      if (java) add("Java/DEX")
      if (kotlin) add("Kotlin")
      if (compose) add("Jetpack Compose")
      if (gradle) add("Android Gradle Plugin")
    }
  }
}
