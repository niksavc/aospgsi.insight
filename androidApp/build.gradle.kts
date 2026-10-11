import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

android {
  namespace = "com.bloxinsight.app"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "com.bloxinsight.manager"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    versionCode = 1
    versionName = "0.1.0"
  }

  androidResources {
    // Lists the app's languages (values-*/) for Android 13+ per-app language settings.
    generateLocaleConfig = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    // Apktool uses java.nio.file, which Android only has from API 26 (minSdk is 24).
    isCoreLibraryDesugaringEnabled = true
  }
}

dependencies {
  implementation(project(":composeApp"))
  implementation(project(":core:apk-model"))
  implementation(project(":core:apk-inspector"))
  implementation(project(":core:verification"))
  implementation(project(":core:installer"))
  implementation(project(":core:patch-engine-apktool"))
  implementation(project(":integrations:backend-api"))
  coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.material3)
}
