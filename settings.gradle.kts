pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // PREFER_PROJECT e non FAIL_ON_PROJECT_REPOS: il plugin Kotlin per wasmJs (modulo :webApp)
    // aggiunge da solo il repository delle distribuzioni di Node.js/Yarn, e con FAIL_ON_PROJECT_REPOS
    // la configurazione falliva. I repository dei moduli app non cambiano: quelli di settings
    // restano gli unici che dichiariamo.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CircolarePlus"
include(":shared")
include(":androidApp")
include(":webApp")
