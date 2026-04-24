// Updated build.gradle.kts content with existing issues fixed.

plugins {
    kotlin("jvm") version "1.5.30"
    application
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
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