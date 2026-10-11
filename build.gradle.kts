plugins {
    // Pin mutually compatible plugin versions after choosing the Android Studio/KMP toolchain.
    // Example versions are deliberately not guessed in this starter scaffold.
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
}
