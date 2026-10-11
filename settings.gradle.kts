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
  }
}

rootProject.name = "BloxInsight"

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
