import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.dokka)
    alias(libs.plugins.publishing)
}

group = "com.wannaverse"
version = "0.5.2"

kotlin {
    jvmToolchain(21)

    android {
        namespace = "com.wannaverse.chimesdk"
        compileSdk = 37
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    swiftPMDependencies {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        swiftPackage(
            url = url("https://github.com/aws/amazon-chime-sdk-ios-spm.git"),
            version = exact("0.27.4"),
            products = listOf(product("AmazonChimeSDK"), product("AmazonChimeSDKMedia"))
        )
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.activity.ktx)

            implementation(libs.chime)
            implementation(libs.chime.media)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()

    if (!project.hasProperty("skipSigning")) signAllPublications()

    coordinates(group.toString(), "chimesdk", version.toString())

    pom {
        name = "kmp-chime-sdk"
        description = "A library to integrate Amazon ChimeSDK into Kotlin Multiplatform"
        inceptionYear = "2026"
        url = "https://github.com/WannaverseOfficial/kmp-chime-sdk"
        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/MIT"
                distribution = "https://opensource.org/licenses/MIT"
            }
        }
        developers {
            developer {
                id = "Wannaverse"
                name = "wannaverse"
                url = "https://github.com/WannaverseOfficial"
            }
        }
        scm {
            url = "https://github.com/WannaverseOfficial/kmp-chime-sdk"
            connection = "scm:git:git://github.com/WannaverseOfficial/kmp-chime-sdk.git"
            developerConnection = "scm:git:ssh://git@github.com/WannaverseOfficial/kmp-chime-sdk.git"
        }
    }
}

afterEvaluate {
    tasks.findByName("cinteropAmazonChimeSDKIosSimulatorArm64")
        ?.dependsOn("podBuildAmazonChimeSDKIosSimulator")
    tasks.findByName("cinteropAmazonChimeSDKIosArm64")
        ?.dependsOn("podBuildAmazonChimeSDKIos")
}

dokka {
    dokkaPublications.html {
        outputDirectory.set(file("$rootDir/docs"))
    }
}
