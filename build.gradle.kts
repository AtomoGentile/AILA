plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    // Non ancora applicato in nessun modulo: serve androidApp/google-services.json, che non
    // esiste finché Simone non crea il progetto Firebase. Vedi FIREBASE_SETUP.md.
    alias(libs.plugins.googleServices) apply false
}
