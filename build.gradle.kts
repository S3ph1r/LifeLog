plugins {
    // Questi alias vengono dal tuo TOML e sono corretti
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false

    // Dichiariamo KSP e Google Services esplicitamente, perché non ci sono nel TOML
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}