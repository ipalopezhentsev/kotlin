plugins {
    kotlin("jvm") version "1.8.10" // Kotlin version to use
    application // Application plugin. Also see 1️⃣ below the code
}

group = "ru.iliks"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    val log4jVersion = "2.20.0"
    val junitVersion = "5.9.2"
    val mockkVersion = "1.13.5"
    val coroutinesVersion = "1.6.4"
    val assertkVersion = "0.25"
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4jVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:$assertkVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
}

tasks.test {
    useJUnitPlatform()
}

kotlin { // Extension for easy setup
    jvmToolchain(17)
}

//application {
//    mainClass.set("MainKt") // The main class of the application
//}