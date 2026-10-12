import java.util.Properties

/**
 * Build file of the Dockshift app module (Kotlin + Jetpack Compose).
 */
plugins {
    // AGP 9 compiles Kotlin on its own (built-in Kotlin), so no kotlin-android plugin
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
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

dependencies {
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
}
