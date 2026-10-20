import com.github.gradle.node.variant.VariantComputer
import com.github.gradle.node.variant.computeNodeExec
import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask

plugins {
    kotlin("jvm") version "2.4.21"
    id("com.strumenta.antlr-kotlin") version "1.0.14"
    application
    id("com.github.node-gradle.node") version "7.1.0"
}

group = "dev.betterclient"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

node {
    version.set("26.12.0")
    download.set(true)
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    implementation("com.strumenta:antlr-kotlin-runtime:1.0.14")
    implementation("org.json:json:20260814")
    testImplementation(kotlin("test"))
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
    mainClass.set("dev.betterclient.scratcher.DebugMain")
}

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.npmInstall)

    val nodeExec = VariantComputer().let { computer ->
        computeNodeExec(node, computer.computeNodeBinDir(node.resolvedNodeDir, node.resolvedPlatform)).get()
    }

    systemProperty("node.executable", nodeExec)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "dev.betterclient.scratcher.cli.CLIKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

kotlin {
    jvmToolchain(11)

    sourceSets {
        main {
            kotlin.srcDir(generateKotlinGrammarSource.map { it.outputDirectory })
        }
    }
}