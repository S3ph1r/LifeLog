
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    // Rimuovi o lascia commentata questa riga obsoleta:
    // alias(libs.plugins.jetbrains.kotlin.composeCompilerExtension)

    // AGGIUNGI QUESTA RIGA per applicare il plugin del compilatore Compose:
    alias(libs.plugins.jetbrains.kotlin.compose)

    alias(libs.plugins.google.ksp) // Assumendo che "google-ksp" sia l'alias nel tuo TOML
    // Se usi Hilt, aggiungi qui l'alias (e assicurati sia definito nel TOML):
    // alias(libs.plugins.google.dagger.hilt.android)
}

android {
    namespace = "com.example.lifelog" // Sintassi Kotlin per l'assegnazione
    compileSdk = libs.versions.compileSdk.get().toInt() // Prendi dal TOML

    defaultConfig {
        applicationId = "com.example.lifelog"
        minSdk = libs.versions.minSdk.get().toInt() // Prendi dal TOML
        targetSdk = libs.versions.targetSdk.get().toInt() // Prendi dal TOML
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Sintassi Kotlin per boolean
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        // La versione del compilatore Compose è ora gestita principalmente dal plugin Kotlin e dalla BOM di Compose.
        // Se hai ancora bisogno di specificarla, assicurati che la versione sia nel TOML.
        // Esempio: kotlinCompilerExtensionVersion = libs.versions.composeCompilerExtension.get()
        // MA VERIFICA: con Kotlin 2.0.x e le BOM recenti, questo potrebbe non essere più necessario
        // o essere gestito in modo diverso (es. tramite il plugin compose.compiler).
        // Per ora la lascio come l'avevi, ma tienila d'occhio. Se causa problemi, commentala.
        kotlinCompilerExtensionVersion = libs.versions.composeCompilerExtension.get() // DEVI DEFINIRE "composeCompilerExtension" nel TOML
    }
    packaging { // Rinominato da packagingOptions
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Dipendenze Core AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.com.google.android.material) // Nome alias da verificare/definire nel TOML

    // Lifecycle (ViewModel, LiveData, Lifecycle Runtime)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose) // Già definito nel tuo TOML

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android) // Verifica se hai definito kotlinx-coroutines-core separatamente o se questo bundle è sufficiente

    // Room (per il database locale)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    // Altre dipendenze che potresti avere o aggiungere in futuro
    // Esempio: WorkManager (dovrai definirlo nel TOML)
    // implementation(libs.androidx.work.runtime.ktx)
    // Esempio: Google Play Services Location (dovrai definirlo nel TOML)
    // implementation(libs.google.android.gms.play.services.location)

    // Testing
    testImplementation(libs.junit.junit) // Alias "junit-junit" nel tuo TOML
    androidTestImplementation(libs.androidx.junit) // Alias "androidx-junit" nel tuo TOML
    androidTestImplementation(libs.androidx.espresso.core) // Alias "androidx-espresso-core" nel tuo TOML
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4) // Alias "androidx-compose-ui-test-junit4" nel tuo TOML
    // debugImplementation(libs.androidx.compose.ui.test.manifest) // Definisci "androidx-compose-ui-test-manifest" nel TOML se vuoi usarlo
    debugImplementation(libs.androidx.compose.ui.tooling) // ui-test-manifest è spesso incluso con ui-tooling o ui-test-junit4 a seconda della versione

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.androidx.preference)

}
