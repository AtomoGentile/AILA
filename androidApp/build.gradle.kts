// =============================================================================
// CIRCOLARE+ — Modulo applicazione Android
//
// Questo file mancava del tutto nel progetto ereditato: senza un build.gradle.kts, ":androidApp"
// non è un modulo Gradle valido (nonostante fosse già elencato in settings.gradle.kts e avesse
// già sorgenti sotto src/androidMain/), quindi il progetto non si sarebbe mai potuto configurare,
// tantomeno compilare. È impostato come modulo Kotlin Multiplatform con il solo target Android
// (da cui il nome della cartella sorgenti "androidMain", identico alla convenzione già usata in
// :shared) invece che come semplice modulo "com.android.application" perché i file esistenti
// sotto src/androidMain/ presuppongono quella struttura.
// =============================================================================

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidApplication)
    // Attivato: androidApp/google-services.json è presente (progetto Firebase "circolare-65aea").
    alias(libs.plugins.googleServices)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                // Splash screen disegnato da noi invece di quello generato da Android a partire
                // dall'icona dell'app (vedi ic_launcher_foreground.xml e res/values/themes.xml).
                implementation(libs.androidx.core.splashscreen)
                implementation(libs.androidx.profileinstaller)
                // Necessario qui (non solo in :shared, dove è "implementation" e quindi non
                // transitivo) perché CircolareMessagingService, che deve stare in questo modulo
                // per essere dichiarato nell'AndroidManifest di :androidApp, estende
                // FirebaseMessagingService.
                // project.dependencies.platform e non il semplice platform(): dentro i blocchi
                // dependencies dei source set KMP quella scorciatoia non esiste piu' da Kotlin
                // 2.1, e la build falliva con "Unresolved reference: platform".
                implementation(project.dependencies.platform(libs.firebase.bom.get()))
                implementation(libs.firebase.messaging)
            }
        }
    }
}

android {
    namespace = "com.circolareplus"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.circolareplus"
        minSdk = 26
        // 36 (Android 16): richiesto da Google Play per gli aggiornamenti dal 31/8/2026. Da 35 la
        // finestra e' sempre edge-to-edge (vedi MainActivity.applySystemBarStyle e
        // design/PlatformInsets.kt); da 36 i blocchi di orientamento non valgono sui tablet, che
        // qui sono comunque liberi di ruotare.
        targetSdk = 36
        // In CI il numero della build di GitHub Actions e l'inizio del commit: nelle Impostazioni
        // ("Versione") si vede quale APK e' davvero installato. Il versionCode cresce a ogni
        // build, cosi' Android tratta ogni APK come un aggiornamento.
        val ciRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        val ciSha = System.getenv("GITHUB_SHA")?.take(7)
        versionCode = ciRun ?: 1
        versionName = if (ciRun != null) "1.0.$ciRun ($ciSha)" else "1.0.0"

        ndk {
            // Le librerie native del motore di AI locale esistono per quattro ABI e da sole
            // pesano un centinaio di MB: includerle tutte porta l'APK di debug da 40 a 131 MB.
            //
            // Si tengono le due che servono davvero: arm64-v8a e' ogni telefono Android moderno
            // (Google Play impone il 64 bit dal 2019), x86_64 e' l'emulatore. Si lasciano fuori
            // armeabi-v7a e x86, cioe' i dispositivi a 32 bit: su quelli il modello non
            // funzionerebbe comunque, perche' un processo a 32 bit ha 4 GB di spazio di
            // indirizzamento in tutto e i pesi del modello ne occupano gia' 2.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // assembleRelease esegue anche il lint "vital": qui non deve bloccare la build (prima,
        // col solo debug, non girava affatto).
        checkReleaseBuilds = false
        abortOnError = false
    }

    // Chiave di rilascio vera, facoltativa: la usa la CI se trova i secret ANDROID_RELEASE_*
    // (vedi android-build.yml). Senza, resta la chiave debug di sempre. ATTENZIONE: passare da una
    // chiave all'altra obbliga chi ha gia' l'app a disinstallarla (perdendo i dati locali) prima
    // di installare la nuova: va fatto una volta sola, prima del rilascio ufficiale.
    val releaseStorePath = System.getenv("ANDROID_RELEASE_KEYSTORE_PATH")
    if (releaseStorePath != null && file(releaseStorePath).exists()) {
        signingConfigs.create("release") {
            storeFile = file(releaseStorePath)
            storePassword = System.getenv("ANDROID_RELEASE_STORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        // L'APK distribuito era il "debug": con Compose e' molto piu' lento (codice non
        // ottimizzato, controlli di debug attivi), da qui gran parte degli scatti nelle
        // animazioni. Il "release" non e' debuggabile e gira a piena velocita'.
        //
        // Firmato con la stessa chiave debug della CI (secret ANDROID_DEBUG_KEYSTORE): stesso
        // applicationId e stessa firma, quindi si installa sopra l'app gia' presente senza
        // disinstallarla. Niente R8/minify per ora: Ktor, serializzazione e Firebase usano la
        // riflessione e andrebbero scritte le regole di ProGuard prima di accenderlo.
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
}
