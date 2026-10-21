pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ShopRef"

// The calculation engine is a plain Kotlin/JVM build so it can be developed and
// unit-tested without the Android SDK (`./gradlew -p core test`).
includeBuild("core")
include(":app")
