plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// PWA di AILA: lo stesso codice Compose di :shared compilato per il browser (WebAssembly).
// Questo modulo contiene solo il punto d'ingresso e i file statici (index.html, manifest, service
// worker, icone). Tutto il resto, schermate comprese, e' in :shared, quindi app e PWA non possono
// divergere.
kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("aila")
        browser {
            commonWebpackConfig {
                outputFileName = "aila.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
            }
        }
    }
}
