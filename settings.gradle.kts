pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    // Apktool 3.0.3 depends on a smali fork that is only published on JitPack (pinned to a
    // commit). Restricted to that group so nothing else can be resolved from there.
    maven("https://jitpack.io") {
      content { includeGroup("com.github.iBotPeaches.smali") }
    }
  }
}

rootProject.name = "AOSP-GSI-Insight"

include(
  ":composeApp",
  ":androidApp",
  ":core:apk-model",
  ":core:patch-api",
  ":core:apk-inspector",
  ":core:patch-engine-apktool",
  ":core:verification",
  ":core:installer",
  ":integrations:backend-api",
)
