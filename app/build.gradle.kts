import java.util.Properties

/**
 * Build file of the Dockshift app module (Kotlin + Jetpack Compose).
 */
plugins {
    // AGP 9 compiles Kotlin on its own (built-in Kotlin), so no kotlin-android plugin
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    // KSP runs Hilt's code generator at compile time
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // Room 3: exports the database schema (see the room3 block below)
    alias(libs.plugins.room3)
    // Serializers for the navigation keys, so the back stack survives process death
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Release signing settings, read from `keystore.properties` in the project root.
 * That file holds the keystore path and passwords, so it is git-ignored and never
 * committed (see `keystore.properties.example`). When it is missing, for example on
 * a fresh clone, the release build is produced unsigned instead of failing.
 */
val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    // Code package; it no longer has to match the applicationId
    namespace = "com.foxnks.dockshift"
    // Latest stable API level (Android 17); Compose 1.12+ requires it
    compileSdk = 37

    defaultConfig {
        // Same id as v1.11, so v2.0 installs as an update of v1.11 (same release key)
        applicationId = "com.foxnks.dayshift.v2"
        // Android 10: no current user is left out
        minSdk = 29
        targetSdk = 37
        // v1.11 is versionCode 2, so every v2 build must be higher
        versionCode = 3
        versionName = "2.0.0-alpha01"

        // Runs instrumented tests (src/androidTest) on an emulator or device
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Only created when keystore.properties exists on this machine
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Debug builds get their own id, so they install next to the real app
            // and can never touch the shifts saved by the installed release
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Shrinking/obfuscation is decided later (release issue), off for now
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign with the release key when it is configured on this machine
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        // JDK 17 everywhere; the Kotlin jvmTarget follows targetCompatibility
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        // Enables Compose tooling (previews, layout inspector)
        compose = true
    }
}

room3 {
    // One JSON file per database version, committed to git. Room compares them to
    // validate every future migration, so a schema change can never silently lose data.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Models and business rules (plain Kotlin)
    implementation(project(":domain"))

    // Activity that hosts the Compose UI (setContent, enableEdgeToEdge)
    implementation(libs.androidx.activity.compose)

    // Compose: the BOM decides the versions of all libraries below it
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Preview rendering and inspection tools, debug builds only
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ViewModel in Compose and lifecycle-aware collection of StateFlow
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation between screens, with a separate ViewModel per screen entry
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.core)

    // Settings storage
    implementation(libs.androidx.datastore.preferences)

    // Dependency injection: runtime, code generator, and hiltViewModel() for Compose
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // Database: Room 3 runtime, its code generator, and SQLite bundled with the app
    // (the same SQLite version on every phone, from Android 10 to the latest)
    implementation(libs.androidx.room3.runtime)
    ksp(libs.androidx.room3.compiler)
    implementation(libs.androidx.sqlite.bundled)

    // Local unit tests (JVM, no emulator)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests: the real database on an emulator or device
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
