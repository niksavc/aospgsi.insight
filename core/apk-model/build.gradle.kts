import com.android.build.gradle.BaseExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
}

subprojects {
    plugins.withId("com.android.library") { configureAndroidJava17() }
    plugins.withId("com.android.application") { configureAndroidJava17() }
}

fun Project.configureAndroidJava17() {
    extensions.configure<BaseExtension> {
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}
