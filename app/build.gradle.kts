import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.3.10"
    id("org.jetbrains.compose") version "1.11.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10"
}

group = "xdman"

val runNumber = System.getenv("GITHUB_RUN_NUMBER") ?: "0"
version = "8.0.$runNumber"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("commons-net:commons-net:3.6")
    implementation("org.tukaani:xz:1.8")
    implementation("com.googlecode.json-simple:json-simple:1.1.1")
    implementation("net.java.dev.jna:jna:5.5.0")
    implementation("net.java.dev.jna:jna-platform:5.5.0")
    implementation("io.github.kdroidfilter:composenativetray-jvm:1.3.0")

    testImplementation(kotlin("test"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjdk-release=23")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_23)
    }
}

tasks.processResources {
    filesMatching("**/version.properties") {
        expand("runNumber" to runNumber)
    }
}

compose.desktop {
    application {
        mainClass = "xdman.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "kdm"
            packageVersion = "8.0.$runNumber"

            linux {
                iconFile.set(project.file("src/main/resources/icons/icon_512.png"))
            }
            windows {
                iconFile.set(project.file("src/main/resources/icons/icon.ico"))
            }
            macOS {
                iconFile.set(project.file("src/main/resources/icons/icon.icns"))
            }
        }
    }
}

// Patch generated .deb with StartupWMClass for proper GNOME dash/dock icon association
// jpackage does not set StartupWMClass in the .desktop file, so we:
// 1. Extract the .deb using dpkg-deb
// 2. Add StartupWMClass=kdm to the desktop file
// 3. Re-package the .deb
tasks.matching { it.name.equals("packageDeb") }.configureEach {
    doLast {
        fileTree(layout.buildDirectory.dir("compose/binaries/main/deb")) {
            include("*.deb")
        }.forEach { debFile ->
            val tmpDir = layout.buildDirectory.dir("deb-patch-tmp").get().asFile
            tmpDir.deleteRecursively()
            tmpDir.mkdirs()
            try {
                // Extract .deb
                val extract = ProcessBuilder("dpkg-deb", "-R", debFile.absolutePath, tmpDir.absolutePath)
                    .inheritIO()
                    .start()
                val extractExit = extract.waitFor()
                if (extractExit != 0) throw RuntimeException("dpkg-deb extract failed with exit code $extractExit")

                // Find and patch .desktop files
                var patched = false
                tmpDir.walkTopDown().filter { it.name.endsWith(".desktop") }.forEach { desktopFile ->
                    val content = desktopFile.readText()
                    if (!content.contains("StartupWMClass")) {
                        desktopFile.appendText("\nStartupWMClass=kdm\n")
                        logger.lifecycle("Patched desktop file: ${desktopFile.absolutePath}")
                        patched = true
                    }
                }

                if (patched) {
                    // Remove old deb
                    debFile.delete()
                    // Re-package with patched desktop file
                    val repackage = ProcessBuilder("dpkg-deb", "-b", tmpDir.absolutePath, debFile.absolutePath)
                        .inheritIO()
                        .start()
                    val repackageExit = repackage.waitFor()
                    if (repackageExit != 0) throw RuntimeException("dpkg-deb repackage failed with exit code $repackageExit")
                    logger.lifecycle("Repackaged .deb with StartupWMClass=kdm: ${debFile.absolutePath}")
                } else {
                    logger.lifecycle("No .desktop files found to patch in .deb")
                }
            } catch (e: Exception) {
                logger.warn("Failed to patch .deb with StartupWMClass: ${e.message}")
            } finally {
                tmpDir.deleteRecursively()
            }
        }
    }
}

tasks.withType<JavaCompile> {
    options.release.set(23)
}
