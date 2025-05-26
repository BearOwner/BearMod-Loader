plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.bearmod.loader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bearmod.loader"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
        }
    }

    lint {
        abortOnError = false
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    // Core Android dependencies
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.constraintlayout.v214)// Use the latest stable version
    //

    // Navigation components
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Lifecycle components
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // SwipeRefreshLayout
    implementation(libs.swiperefreshlayout)

    // Database
    implementation(libs.room.runtime)
    implementation(libs.fragment)
    implementation(libs.recyclerview)
    annotationProcessor(libs.room.compiler)

    // Image loading
    implementation(libs.glide)

    // UI effects
    implementation(libs.shimmer)
    implementation(libs.lottie)

    // ExoPlayer for video playback
    implementation(libs.exoplayer)

    // Direct KeyAuth API implementation
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.json)

    // Room database
    implementation(libs.room.runtime.v271)
    implementation(libs.room.ktx.v271)
    annotationProcessor(libs.room.compiler.v271)

    // OkHttp for networking
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Retrofit for API calls
    implementation(libs.retrofit.v2110)
    implementation(libs.converter.gson.v2110)
    implementation(libs.fragment)

    // WorkManager
    implementation(libs.work.runtime)
    implementation(libs.room.ktx)
    // KeyAuth SDK replaced with simple HTTP implementation
    // implementation(libs.keyauth.java.api)
    implementation(libs.work.runtime.ktx) // Add this for Kotlin coroutines

    // For ListenableFuture support
    implementation(libs.concurrent.futures)
    implementation(libs.guava)

    // WebSocket
    implementation(libs.websocket)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.mockito.android)
}