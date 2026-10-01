pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net")
        maven("https://jitpack.io")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.replaymod.preprocess") {
                useModule("com.github.Fallen-Breath:preprocessor:d452ef7")
            }
        }
    }
}

rootProject.name = "altoclef"