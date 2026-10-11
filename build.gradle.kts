plugins {
  // Plugin versions live in gradle/libs.versions.toml; keep them mutually compatible.
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.kotlin.multiplatform.library) apply false
  alias(libs.plugins.compose.multiplatform) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.spotless)
}

// Code style is ktfmt (Google style). Run `gradle spotlessApply` to fix formatting.
spotless {
  kotlin {
    target("**/*.kt")
    targetExclude("**/build/**")
    ktfmt(libs.versions.ktfmt.get()).googleStyle()
  }
  kotlinGradle {
    target("**/*.gradle.kts")
    targetExclude("**/build/**")
    ktfmt(libs.versions.ktfmt.get()).googleStyle()
  }
}

// Show assertion messages and stack traces in the console log, so CI failures are readable.
subprojects {
  tasks.withType<Test>().configureEach {
    testLogging {
      events("failed")
      exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
      showStackTraces = true
    }
  }
}
