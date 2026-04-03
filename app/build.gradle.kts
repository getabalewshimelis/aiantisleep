plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.gech.antisleepdetector"
    compileSdk = 36 // Changed to 34 (Stable) to fix the "SDK not found" error

    defaultConfig {
        applicationId = "com.gech.antisleepdetector"
        minSdk = 24
        targetSdk = 35 // Changed to 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        ndk {
            // Explicitly include ABIs to help MediaPipe find the right binaries
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86_64"))
        }
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.usb.serial)
    implementation(libs.mp.android.chart)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // MediaPipe Face Mesh (Legacy Solutions API)
    implementation(libs.mediapipe.face.mesh)
    implementation(libs.mediapipe.face.mesh.solution)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}