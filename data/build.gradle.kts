plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:connectivity"))
    implementation(project(":domain:chat"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}