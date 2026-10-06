import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// ============================================================================
// SPEECH SPLIT FOR WINDOWS (desktop)
// Builds the Windows installer from the same code as the Android app:
//   core/ and ui/ come straight from the app module, only desktop/ is desktop-specific.
//
// Run on this computer:      ./gradlew :desktop:run
// Windows installer (.msi):  ./gradlew :desktop:packageMsi   (must run ON Windows; GitHub does this)
// Mac app (.dmg):            ./gradlew :desktop:packageDmg
// ============================================================================

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val appVersion = providers.gradleProperty("speechSplitVersion").get()
val shared = "../app/src/main/java/no/srrlsm/speechsplit"

kotlin {
    jvmToolchain(21)
    sourceSets {
        named("main") {
            // The shared parts of the Android app: app logic, screens, JSON format
            kotlin.srcDir("$shared/core")
            kotlin.srcDir("$shared/ui")
            kotlin.srcDir("$shared/jvm")
        }
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3.desktop)
    implementation(libs.compose.material.icons.extended.desktop)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.json)
}

compose.desktop {
    application {
        mainClass = "no.srrlsm.speechsplit.desktop.MainKt"

        // Package with a full JDK 21 (downloaded automatically), which includes the installer tools
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }.get().metadata.installationPath.asFile.absolutePath

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg)
            packageName = "Speech Split"
            // Installers need three numbers: "2.0" becomes "2.0.0"
            packageVersion = appVersion.split(".").let { (it + listOf("0", "0")).take(3) }.joinToString(".")
            description = "Speech timer with segments"
            vendor = "SRRLSM"
            copyright = "© 2026 SRRLSM"
            modules("java.naming", "java.instrument", "jdk.unsupported")

            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "Speech Split"
                shortcut = true          // desktop shortcut
                menu = true              // Start menu entry
                perUserInstall = true    // installs without admin rights
                dirChooser = false
                // Never change this: it lets new versions replace the old one instead of installing twice
                upgradeUuid = "6b0f6d43-2a8e-4c4e-9b1e-3f1d2c7a9e51"
            }
            macOS {
                iconFile.set(project.file("icons/icon.icns"))
                bundleID = "no.srrlsm.speechsplit"
            }
            linux {
                iconFile.set(project.file("icons/icon.png"))
            }
        }
    }
}

// Developer tool: renders the main screens to PNG files in desktop/build/screenshots
tasks.register<JavaExec>("screenshots") {
    group = "speech split"
    description = "Renders the main screens to PNG files."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("no.srrlsm.speechsplit.desktop.ScreenshotsKt")
    args(layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    jvmArgs("-Djava.awt.headless=true")
}
