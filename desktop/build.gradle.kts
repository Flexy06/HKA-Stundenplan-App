import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Version wie bei der Android-App: auf GitHub Actions die Build-Nummer
val ciBuild = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val appVersion = "1.0.${ciBuild ?: 0}"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

sourceSets {
    main {
        // Gemeinsame Logik mit der Android-App
        kotlin.srcDir("../shared/src")
        // Campuskarte (campus_map.txt) aus der Android-App mitnehmen
        resources.srcDir("../app/src/main/assets")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("org.json:json:20240303")
}

compose.desktop {
    application {
        mainClass = "de.flexy.stundenplan.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "HKA Stundenplan"
            packageVersion = appVersion
            description = "Stundenplan der Hochschule Karlsruhe (Raumzeit)"
            vendor = "Flexy06"
            copyright = "MIT-Lizenz"
            // HTTPS (ECDHE), deutsche Wochentage/Monate, Tray/AWT
            modules("java.desktop", "java.logging", "jdk.crypto.ec", "jdk.localedata", "java.naming")
            windows {
                iconFile.set(project.file("icon.ico"))
                menu = true
                menuGroup = "HKA Stundenplan"
                shortcut = true
                perUserInstall = true
                dirChooser = false
                upgradeUuid = "6F7C3E1A-2B4D-4E8F-9A1C-5D3B7E9F0A21"
            }
        }
    }
}
