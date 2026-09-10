plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
}

// Package esplicito per la classe Res generata dalle risorse Compose (shared/src/commonMain/
// composeResources/...): fissato a mano invece di lasciarlo al default per sapere con certezza
// cosa importare in LoadingScreen.kt (import circolareplus.generated.resources.Res / .aila_logo).
compose.resources {
    packageOfResClass = "circolareplus.generated.resources"
    publicResClass = true
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // Target iOS (device ARM64 + simulatore Intel/ARM64): produce un framework "shared.framework"
    // che il progetto Xcode importa. Senza questi target il codice in src/iosMain non fa parte
    // della build (era così nella versione precedente: cartella presente ma non compilata).
    // iosX64, cioe' il simulatore su Mac Intel, non c'e' piu': Compose Multiplatform 1.12 ha
    // smesso di pubblicare per quel target e la build falliva con "Unresolved platforms:
    // [iosX64]". Non e' una perdita — i Mac Intel non ricevono piu' le nuove versioni di Xcode —
    // e restano il dispositivo vero (iosArm64) e il simulatore su Apple Silicon.
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)
        }
        val androidMain by getting {
            dependencies {
                implementation(compose.ui)
                implementation(libs.kotlinx.coroutines.core)
                // Per PlatformBackHandler.android.kt (androidx.activity.compose.BackHandler):
                // gestisce il tasto/gesto "indietro" di sistema. "implementation" qui non basta a
                // renderlo visibile a :androidApp (stesso discorso già fatto per Firebase
                // Messaging), ma qui in :shared serve comunque per compilare questo file.
                implementation(libs.androidx.activity.compose)
                // Motore HTTP Ktor per Android
                implementation(libs.ktor.client.okhttp)
                // Estrazione testo dai PDF delle circolari (classificazione AI client-side)
                implementation(libs.pdfbox.android)
                // AI locale: inferenza LLM sul telefono, senza rete e senza API key.
                // "api" e non "implementation" perche' il modulo :androidApp deve poter
                // impacchettare le librerie native (.so) del motore: con implementation la
                // dipendenza non e' transitiva e l'APK verrebbe fuori senza il runtime,
                // facendo fallire il caricamento del modello solo a esecuzione (lo stesso
                // problema gia' incontrato con Firebase Messaging).
                api(libs.litertlm.android)
                // WorkManager: usata per il download del modello locale, il download dei PDF e
                // la classificazione in coda, cosi' da sopravvivere a schermo spento o app in
                // background (vedi CircularsBackgroundWorkers.android.kt). "api" per lo stesso
                // motivo di litertlm-android sopra: :androidApp deve vedere le classi runtime di
                // WorkManager per registrare/osservare i worker dalle Impostazioni.
                api(libs.androidx.work.runtime.ktx)
                // Notifiche push (FCM). Si compila già ora; funziona solo dopo aver completato
                // la configurazione Firebase descritta in FIREBASE_SETUP.md (altrimenti
                // PushTokenProvider intercetta l'eccezione e restituisce null, vedi quel file).
                // project.dependencies.platform e non il semplice platform(): dentro i blocchi
                // dependencies dei source set KMP quella scorciatoia non esiste piu' da Kotlin
                // 2.1, e la build falliva con "Unresolved reference: platform".
                implementation(project.dependencies.platform(libs.firebase.bom.get()))
                implementation(libs.firebase.messaging)
            }
        }
        // Source set intermedio "iosMain" creato automaticamente dal hierarchy template di Kotlin
        // (condiviso da iosX64Main/iosArm64Main/iosSimulatorArm64Main) — qui il motore HTTP Darwin.
        val iosMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "circolareplus.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
