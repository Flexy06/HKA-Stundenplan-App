import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.flexy.stundenplan"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.flexy.stundenplan"
        minSdk = 26
        targetSdk = 36
        // Auf GitHub Actions zählt die Build-Nummer hoch, damit Updates sich über die alte Version installieren lassen
        val ciBuild = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = ciBuild?.let { 100 + it } ?: 1
        versionName = ciBuild?.let { "1.0.$it" } ?: "1.0.0-dev"
    }

    // Eigener Schlüssel im Repo: Android Studio und GitHub signieren identisch,
    // so lassen sich beide APKs gegenseitig drüber-installieren. (Repo privat halten!)
    signingConfigs {
        create("shared") {
            storeFile = file("signing/stundenplan.jks")
            storePassword = "stundenplan"
            keyAlias = "stundenplan"
            keyPassword = "stundenplan"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3:1.4.0-alpha15")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    testImplementation("junit:junit:4.13.2")
}
