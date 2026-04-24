// Updated build.gradle.kts content with existing issues fixed.

plugins {
    kotlin("jvm") version "1.5.30"
    application
}

application {
    mainClass.set("com.example.MainKt")
}

dependencies {
    implementation(kotlin("stdlib"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}