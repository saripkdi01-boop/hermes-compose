plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.chaquopy)
}
android {
    namespace = "id.sukiapps.hermes"
    compileSdk = 34
    defaultConfig {
        applicationId = "id.sukiapps.hermes"
        minSdk = 30
        targetSdk = 34
        versionCode = 54
        versionName = "14.30"
        // D1 (14.30): Chaquopy butuh ABI native Python. arm64-v8a = semua HP
        // target (A76 dkk); x86_64 = emulator. Tiap ABI menambah MB APK —
        // delta diukur dari artifact CI dan dilaporkan di deskripsi PR.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }
    buildTypes {
        release {
            // CI menghasilkan APK unsigned; signing dilakukan maintainer
            // dengan keystore resmi sebelum didistribusikan.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
// D1 (14.30): Chaquopy 16.0.0 — AGP proyek 8.7.3 ada dalam rentang dukungannya
// (7.0–8.8) dan minSdk 30 ≥ 24. Python 3.11; buildPython autodetect CI (bila
// minor version tidak cocok, kompilasi pyc hanya warning, build tetap jalan).
chaquopy {
    defaultConfig {
        version = "3.11"
    }
}
dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    // Compose (versi dikelola BOM)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.navigation.compose)
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    // Unit test JVM
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
