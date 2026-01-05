plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.androidtviptvapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.androidtviptvapp"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Vector drawable support for older APIs
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            // Faster debug builds
            isDebuggable = true
        }

        release {
            // Enable code shrinking, obfuscation, and optimization
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Optimize for release
            isDebuggable = false

            // Sign with release key (configure your keystore)
            // signingConfig = signingConfigs.getByName("release")
        }
    }

    // Split APKs by ABI for smaller downloads (optional - useful for Play Store)
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true // Also generate universal APK
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            // Enable Compose compiler optimizations
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi"
        )
    }

    buildFeatures {
        compose = true
        // Disable unused build features
        buildConfig = false
        aidl = false
        renderScript = false
        shaders = false
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }

    // Optimize packaging
    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/*.kotlin_module"
            )
        }
    }

    // Lint configuration
    lint {
        abortOnError = false
        checkReleaseBuilds = true
        disable += listOf("MissingTranslation", "ExtraTranslation")
    }
}

dependencies {
    // Compose versions - use consistent versions
    val composeVersion = "1.5.4"
    val composeMaterial3Version = "1.1.2"
    val media3Version = "1.2.0"

    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose UI
    implementation("androidx.compose.ui:ui:$composeVersion")
    implementation("androidx.compose.ui:ui-graphics:$composeVersion")
    implementation("androidx.compose.material3:material3:$composeMaterial3Version")

    // TV Compose - Essential for TV apps
    implementation("androidx.tv:tv-foundation:1.0.0-alpha10")
    implementation("androidx.tv:tv-material:1.0.0-alpha10")

    // Media3 (ExoPlayer) - Only include what's needed
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")  // HLS streams
    implementation("androidx.media3:media3-ui:$media3Version")
    // Only add DASH if you use DASH streams:
    // implementation("androidx.media3:media3-exoplayer-dash:$media3Version")

    // Image Loading - Coil is lightweight
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-svg:2.5.0")  // Only if using SVG logos

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Material Icons - needed for extended icons (QrCode, SportsEsports, etc.)
    implementation("androidx.compose.material:material-icons-core:$composeVersion")
    implementation("androidx.compose.material:material-icons-extended:$composeVersion")

    // Network - Retrofit + OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // Gson is included transitively, but explicit version ensures consistency
    implementation("com.google.code.gson:gson:2.10.1")
    // OkHttp is included transitively by Retrofit

    // QR Code - Only if needed for pairing/login
    implementation("com.google.zxing:core:3.5.2")

    // Debug only
    debugImplementation("androidx.compose.ui:ui-tooling:$composeVersion")
    debugImplementation("androidx.compose.ui:ui-tooling-preview:$composeVersion")
}
