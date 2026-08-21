plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kolktech.kahawai"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kolktech.kahawai"
        minSdk = 26
        targetSdk = 36
        // Overridden by the release workflow (-PversionCode=<run number>
        // -PversionName=<git tag>) so tagged builds carry a real,
        // monotonically increasing version; local builds keep these defaults.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = project.findProperty("versionName") as String? ?: "0.1.0"
    }

    // Release signing key, supplied by CI via env vars (see
    // .github/workflows/release.yml) and never committed. Falls back to
    // the debug keystore when unset so `assembleRelease` still works
    // locally for on-device perf testing — that build just isn't
    // update-compatible with CI-signed releases.
    val releaseStoreFile = System.getenv("RELEASE_STORE_FILE")
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Side-by-side with an installed release build, so a test APK
        // doesn't cost you the working one (label: src/debug/res).
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // WindowSizeClass (currentWindowAdaptiveInfo) for tablet/foldable
    // layout decisions — see DetailScreen's two-pane switch.
    implementation(libs.androidx.compose.material3.adaptive)
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

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
