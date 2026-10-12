import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Build file of the :domain module: models and business rules in plain Kotlin.
 *
 * This is a JVM library, so the Android SDK is not on its classpath at all:
 * any `android.*` import here fails to compile. That keeps the rules testable
 * with plain JUnit, without an emulator.
 */
plugins {
    // `java-library` provides the `api` configuration used below
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

java {
    // Same bytecode level as the :app module
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // `api`: Flow appears in this module's public interfaces, so modules
    // that depend on :domain need coroutines on their classpath too
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
