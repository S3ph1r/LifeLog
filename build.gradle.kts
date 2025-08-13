// Top-level build file
plugins {
    // Alias corretto per il plugin Android, come definito nel tuo toml
    alias(libs.plugins.android.application) apply false

    // Alias corretto per il plugin Kotlin, come definito nel tuo toml
    alias(libs.plugins.kotlin.android) apply false

    // Dichiariamo KSP con il suo ID esplicito e la versione.

    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}