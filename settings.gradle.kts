pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
        maven("https://maven.fabricmc.net")
    }

    // Maps the plugin ID to its actual JitPack artifact coordinate
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.replaymod.preprocess") {
                useModule("com.github.replaymod:preprocessor:${requested.version}")
            }
        }
    }
}