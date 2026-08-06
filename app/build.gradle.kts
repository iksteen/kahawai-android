plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kolktech.kahawai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kolktech.kahawai"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tink.android)
    // Installs Jetpack libraries' bundled baseline profiles (Compose,
    // etc.) into ART on first run so their hot paths are AOT-compiled
    // instead of interpreted/JIT-warming — the difference is most visible
    // on slow devices' cold start and first scroll. A full app-specific
    // baseline profile (covering our own screens) needs a macrobenchmark
    // module driven by an instrumented test on a connected device/emulator
    // to generate, which this change doesn't add.
    implementation(libs.androidx.profileinstaller)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Faithful ASS/SSA subtitle rendering (libass). ass-media isn't wired
    // into the RenderersFactory — the hub's ASS tracks arrive out-of-band
    // (a streamed .ass tap keyed to a track id), not muxed into the HLS
    // stream, so AssHandler/AssTrack are driven directly instead of
    // through Media3's own track/renderer pipeline. The dependency is
    // still needed for AssSubtitleView/AssHandler (render + overlay view).
    implementation(libs.ass)
    implementation(libs.ass.kt)
    implementation(libs.ass.media)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
