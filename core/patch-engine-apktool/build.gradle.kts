import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
  android {
    namespace = "com.bloxinsight.core.patchengine.apktool"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
    withHostTest {}

    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":core:patch-api"))
    }
    androidMain.dependencies {
      implementation(libs.apktool.lib)
      implementation(libs.apksig)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}
