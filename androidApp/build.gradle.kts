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
  namespace = "nks.aospgsi.insight.app"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "nks.aospgsi.insight"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    // CI passes an increasing number for release builds; local builds stay at 1.
    versionCode = System.getenv("AOSPGSI_VERSION_CODE")?.toIntOrNull() ?: 1
    versionName = "0.1.0"
  }

  signingConfigs {
    // A fixed debug key checked into the repository. Without it every CI run signs with a fresh
    // random key, and Android refuses to update an installed build with the next one ("App not
    // installed"). This key is public: it is for test builds only, never for a release.
    getByName("debug") {
      storeFile = file("debug.keystore")
      storePassword = "android"
      keyAlias = "aospgsi-debug"
      keyPassword = "android"
    }

    // The release key never lives in the repository: CI hands it over through the environment
    // (see the "release" input of .github/workflows/build.yml). Without it the release APK stays
    // unsigned, which Android will not install.
    val releaseKeystore = System.getenv("AOSPGSI_RELEASE_KEYSTORE")
    if (releaseKeystore != null) {
      create("release") {
        storeFile = file(releaseKeystore)
        storeType = "pkcs12"
        storePassword = System.getenv("AOSPGSI_RELEASE_PASSWORD")
        keyAlias = System.getenv("AOSPGSI_RELEASE_ALIAS")
        keyPassword = System.getenv("AOSPGSI_RELEASE_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      signingConfig = signingConfigs.findByName("release")
      // Not shrunk yet: Apktool, apksig and the catalogs reflect over classes and resources, so
      // R8 rules have to be written and tested on a device before this is turned on.
      isMinifyEnabled = false
    }
  }

  androidResources {
    // Lists the app's languages (values-*/) for Android 13+ per-app language settings.
    generateLocaleConfig = true
  }

  packaging {
    // aapt2 ships as libaapt2.so and is run from the native library directory, so the libraries
    // must be extracted at install time instead of being mapped from the APK.
    jniLibs { useLegacyPackaging = true }
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
  implementation(project(":core:monetization"))
  implementation(project(":core:patch-api"))
  implementation(project(":core:patch-engine-apktool"))
  implementation(project(":integrations:backend-api"))
  coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.material3)
}
