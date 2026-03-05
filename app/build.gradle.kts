plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.batteryguardian"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.batteryguardian"
        minSdk = 21
        targetSdk = 34
        versionCode = 4
        versionName = "1.0.4"
    }


// --- Signing (Release) ---
// Place your keystore at: <project-root>/release.jks
// And put passwords in ~/.gradle/gradle.properties:
// BG_STORE_PASSWORD=...
// BG_KEY_PASSWORD=...
// (Alias is fixed to 'release' for simplicity)
signingConfigs {
    // Ensure debug builds also use modern signing
    getByName("debug") {
        enableV1Signing = true
        enableV2Signing = true
        enableV3Signing = true
    }
    create("release") {
        // Use a simple, predictable keystore file name at project root
        storeFile = file("../release.jks")
        keyAlias = "release"

        // Read secrets from Gradle properties (recommended)
        val sp = (project.findProperty("BG_STORE_PASSWORD") as String?) ?: ""
        val kp = (project.findProperty("BG_KEY_PASSWORD") as String?) ?: ""
        storePassword = sp
        keyPassword = kp

        enableV1Signing = true
        enableV2Signing = true
        enableV3Signing = true
    }
}

    buildTypes {
        release {
            isMinifyEnabled = false

// Prefer release keystore if present & configured; otherwise fall back to debug signing
signingConfig = if (file("../release.jks").exists() &&
    !((project.findProperty("BG_STORE_PASSWORD") as String?) ?: "").isBlank() &&
    !((project.findProperty("BG_KEY_PASSWORD") as String?) ?: "").isBlank()
) {
    signingConfigs.getByName("release")
} else {
    signingConfigs.getByName("debug")
}
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.preference:preference-ktx:1.2.1")

    compileOnly("de.robv.android.xposed:api:82")
}
