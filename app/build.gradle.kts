import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val youtubeApiKey = providers.gradleProperty("YOUTUBE_API_KEY").orElse("")
val escapedYouTubeApiKey = youtubeApiKey.get()
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

val deepenKeystorePath = providers.gradleProperty("DEEPEN_KEYSTORE_PATH").orElse("")
val deepenKeystorePassword = providers.gradleProperty("DEEPEN_KEYSTORE_PASSWORD").orElse("")
val deepenKeyAlias = providers.gradleProperty("DEEPEN_KEY_ALIAS").orElse("")
val deepenKeyPassword = providers.gradleProperty("DEEPEN_KEY_PASSWORD").orElse("")
val deepenVersionCode = providers.gradleProperty("VERSION_CODE").orElse("2")
val deepenVersionName = providers.gradleProperty("VERSION_NAME").orElse("0.2.0")

android {
    namespace = "rw.kitech.deepen"
    compileSdk = 37

    defaultConfig {
        applicationId = "rw.kitech.deepen"
        minSdk = 23
        targetSdk = 37
        versionCode = deepenVersionCode.get().toInt()
        versionName = deepenVersionName.get()

        buildConfigField(
            "String",
            "YOUTUBE_API_KEY",
            "\"$escapedYouTubeApiKey\""
        )
    }

    signingConfigs {
        create("release") {
            val keystorePath = deepenKeystorePath.get()
            if (keystorePath.isNotBlank()) {
                storeFile = file(keystorePath)
                storePassword = deepenKeystorePassword.get()
                keyAlias = deepenKeyAlias.get()
                keyPassword = deepenKeyPassword.get()
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
