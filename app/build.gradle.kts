import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
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
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjdk-release=23")
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
        }
    }
}

tasks.withType<JavaCompile> {
    options.release.set(23)
}
