import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    application
    // Bundles your bot + all dependencies into one runnable jar (./gradlew shadowJar)
    id("com.gradleup.shadow") version "9.4.2"
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        // Needs a JDK 21 installed. Change to 17 if that's what you have.
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Discord
    implementation("net.dv8tion:JDA:6.6.0") {
        // Voice/audio natives aren't needed for a text chatbot, so this keeps the jar smaller
        exclude(module = "opus-java")
    }

    // Gemini (official Google Gen AI Java SDK)
    implementation("com.google.genai:google-genai:1.78.0")

    // Logging backend, otherwise JDA prints an SLF4J/no provider warning
    implementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("dev.romulus_lanceues.Cloud")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // Keeps parameter names, which the Gemini SDK uses for automatic function calling
    options.compilerArgs.add("-parameters")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("gemini-discord-bot")
    archiveClassifier.set("")
    // Prevents broken service registrations when several libraries are merged into one jar
    mergeServiceFiles()
}