plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false

    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude(layout.buildDirectory)
        ktlint().setEditorConfigPath("$rootDir/.editorconfig")
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint()
    }
}
