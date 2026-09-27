import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Coroutines & date/heure multiplateforme (remplace java.util.Calendar)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)

            // ViewModel multiplateforme (fork JetBrains, même API que androidx.lifecycle)
            implementation(libs.jb.lifecycle.viewmodel)

            // Runtime Compose : requis par le plugin Compose Compiler dès qu'il est actif,
            // même sans aucune fonction @Composable dans ce module.
            implementation(compose.runtime)
            // Catalogue de ressources textuelles partagé (Compose Multiplatform Resources)
            implementation(compose.components.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            // Rien de spécifique : le domaine partagé est 100% commun,
            // aucune dépendance Android n'est requise côté androidMain.
        }
    }
}

android {
    namespace = "com.example.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.resources {
    packageOfResClass = "com.example.shared.resources"
    generateResClass = always
    publicResClass = true
}
