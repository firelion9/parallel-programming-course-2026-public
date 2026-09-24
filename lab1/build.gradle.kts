plugins {
    kotlin("jvm") version "2.4.0"
    application
}

group = "com.fluffyspire.pollen"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(18)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "RunnerKt"
}