plugins {
    kotlin("jvm") version "2.0.21"
    application
}

application {
    mainClass.set("dev.marko.lsp.logo.MainKt")
}

group = "dev.marko"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}