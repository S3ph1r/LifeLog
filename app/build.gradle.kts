// File: build.gradle (Module :app)

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // --- AGGIUNTO IL PLUGIN KSP ---
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
}

android {
    namespace = "com.example.lifelog"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.lifelog"
        minSdk = 33
        targetSdk = 35
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
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    // Core Android & UI
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.0")

    // Lifecycle (ViewModel, LiveData)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.0")

    // Preference (Schermata Impostazioni)
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Security (Criptazione)
    implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")

    // WorkManager (Task in background)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // --- AGGIUNTE LE DIPENDENZE DI ROOM ---
    // Room (Database Persistente)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1") // Supporto a Coroutines e Flow
    ksp("androidx.room:room-compiler:2.6.1")      // Annotation processor (KSP)
    // ------------------------------------

    // Retrofit (Rete)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Testing (standard)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Location
    implementation("com.google.android.gms:play-services-location:21.2.0")
}