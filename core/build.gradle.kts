import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.1.0"
}

group = "com.shopref"
version = "1.0"

kotlin {
    // JVM: consumed by the Android app and used to run the unit tests.
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // JS: the same engine compiled to a single UMD script for the web app (see /web).
    js(IR) {
        moduleName = "shopref"
        nodejs {
            testTask { enabled = false }
        }
        compilerOptions {
            moduleKind.set(JsModuleKind.MODULE_UMD)
        }
        binaries.executable()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
