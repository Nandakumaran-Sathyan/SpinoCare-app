plugins { 
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization") version "2.0.21"
    kotlin("kapt")
    id("com.google.dagger.hilt.android")
}

// Apply the Google services plugin to make google-services.json values available to Firebase SDKs.
// This plugin is declared in the project-level `build.gradle.kts` (id("com.google.gms.google-services") version "4.4.4" apply false).
// If you don't want to enable Firebase in your environment, you can remove or comment this line.
apply(plugin = "com.google.gms.google-services")

android {
    namespace = "com.example.modicanalyzer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.modicanlyzer"
        minSdk = 26  // Updated to Android O (API 26) to support MethodHandle APIs
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true  // Enable desugaring for better API compatibility
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    
    // Prevent Android from compressing TF Lite model files (modern Kotlin DSL syntax)
    androidResources {
        noCompress += "tflite"
    }
}

// LiteRT dependency resolution (no conflicts)
configurations.all {
    resolutionStrategy {
        // Force LiteRT version for consistency
        force("com.google.ai.edge.litert:litert:1.0.1")
    }
}

dependencies {
    // Room Database - Local offline caching
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // WorkManager - Background sync jobs
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Hilt - Dependency Injection
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-work:1.1.0")
    kapt("androidx.hilt:hilt-compiler:1.1.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Firestore - Cloud database
    implementation("com.google.firebase:firebase-firestore-ktx:24.10.3")
    
    // Firebase Storage - Image/file storage
    implementation("com.google.firebase:firebase-storage-ktx:20.3.0")
    
    // Coroutines - Async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // Lifecycle components - ViewModel & LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
    // uCrop - Image cropping library
    implementation("com.github.yalantis:ucrop:2.2.8")
    
    // LiteRT 2025 - Google's future-proof solution (eliminates conflicts)
    implementation("com.google.ai.edge.litert:litert:1.0.1")
    
    // Task API for official async pattern
    implementation("com.google.android.gms:play-services-tasks:18.0.2")
    
    // HTTP client for federated learning and remote inference
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Retrofit - REST API client
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")
    
    // JSON serialization for API communication
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Core library desugaring for better API compatibility
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Firebase BoM - ensures compatible versions for all Firebase libraries
    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))

    // Firebase libraries - Explicitly versioned to resolve dependency issue
    implementation("com.google.firebase:firebase-auth-ktx:22.4.0")
    implementation("com.google.firebase:firebase-analytics-ktx:22.0.0")
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    testImplementation(libs.junit)
                                                                                                // Unit testing utilities
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.mockito:mockito-core:5.4.0")
    testImplementation("org.mockito:mockito-inline:5.4.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Compatibility: some CI or tooling may request ':app:testClasses' which isn't present
// in Android modules. Register a safe no-op task to avoid failures.
tasks.register("testClasses") {
    // This task intentionally left blank. It prevents external tooling from failing
    // when they try to query or execute ':app:testClasses'.
}