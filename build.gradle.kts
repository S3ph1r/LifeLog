// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false

    // MODIFICA QUESTA RIGA:
    // alias(libs.plugins.kotlinComposeCompiler) // RIMUOVI O COMMENTA QUESTA
    // USA L'ALIAS CORRETTO DAL TUO LIBS.VERSIONS.TOML e aggiungi 'apply false':
    alias(libs.plugins.jetbrains.kotlin.compose) apply false
}
