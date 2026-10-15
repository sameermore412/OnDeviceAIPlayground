import java.util.Properties

plugins {
    // AGP 9 compiles Kotlin itself, so there is no separate kotlin-android plugin.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Optional secrets from local.properties, which isn't checked in.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.sameer.ondeviceai"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "com.sameer.ondeviceai"
        minSdk = 31 // Android 12, same as Google's AI Edge Gallery, which ships LiteRT-LM
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Brave Search API key for the chat's webSearch tool. Without one it searches Wikipedia.
        buildConfigField("String", "BRAVE_API_KEY", "\"${localProperties.getProperty("brave.api.key", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs { pickFirsts.add("**/liblitertlm_jni.so") }
    }
}

kotlin {
    compilerOptions {
        // LiteRT-LM is compiled with a newer Kotlin than this project (2.4 vs 2.2), as in Gallery.
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.litertlm.android)
    implementation(libs.kotlin.reflect)
}
