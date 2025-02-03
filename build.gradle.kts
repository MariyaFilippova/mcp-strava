plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "org.example"
version = "0.1.0"


application {
    mainClass.set("MainKt")
}

tasks.shadowJar {
    archiveBaseName.set("strava-mcp-server")
    archiveVersion.set("1.0.0")
    mergeServiceFiles()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-mcp-sdk:0.1.0")
    implementation("org.slf4j:slf4j-nop:2.0.9")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.2")
    implementation("io.ktor:ktor-client-json:3.0.2")
    implementation("com.github.dotenv-org:dotenv-vault-kotlin:0.0.2")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
