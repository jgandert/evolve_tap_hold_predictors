plugins {
    kotlin("jvm") version "2.2.0"
}

group = "io.github.jgandert"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    val jeneticsVersion = "8.2.0"

    implementation("com.github.haifengl:smile-kotlin:4.3.0")
    implementation("io.jenetics:jenetics:$jeneticsVersion")
    implementation("io.jenetics:jenetics.prog:$jeneticsVersion")
}

kotlin {
    jvmToolchain(23)
}