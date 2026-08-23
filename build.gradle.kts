import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask

plugins {
    kotlin("jvm") version "2.3.21"
    id("com.strumenta.antlr-kotlin") version "1.0.10"
    application
}

group = "dev.betterclient"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.strumenta:antlr-kotlin-runtime:1.0.10")
    implementation("org.json:json:20250517")
}

val generateKotlinGrammarSource = tasks.register<AntlrKotlinTask>("generateKotlinGrammarSource") {
    source = fileTree(layout.projectDirectory.dir("antlr")) {
        include("**/*.g4")
    }

    packageName = "com.strumenta.antlrkotlin.parsers.generated"
    arguments = listOf("-visitor")

    outputDirectory = layout.buildDirectory
        .dir("generatedAntlr/com/strumenta/antlrkotlin/parsers/generated")
        .get()
        .asFile
}

application {
    mainClass.set("dev.betterclient.scratcher.MainKt")
}

kotlin {
    jvmToolchain(25)

    sourceSets {
        main {
            kotlin.srcDir(generateKotlinGrammarSource.map { it.outputDirectory })
        }
    }
}