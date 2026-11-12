plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.nextspot.app"
    compileSdk = 34

    // Android Automotive car APIs (CarPropertyManager). Used only by the "pleos" variant.
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.nextspot.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    // Two build variants:
    //  - sim   : runs on ANY Android emulator. Buttons stand in for car signals.
    //  - pleos : uses the real Pleos Connect SDK. Needs SDK access + Pleos Connect Emulator.
    flavorDimensions += "platform"
    productFlavors {
        create("sim") {
            dimension = "platform"
            isDefault = true
        }
        create("pleos") {
            dimension = "platform"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Demo only: sign the release APK with the debug key so it installs anywhere.
            signingConfig = signingConfigs.getByName("debug")
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
        compose = true
    }
}

dependencies {
    // UI (Jetpack Compose)
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")

    // Firebase Firestore (shared database between the two cars)
    implementation(platform("com.google.firebase:firebase-bom:33.3.0"))
    implementation("com.google.firebase:firebase-firestore")

    // Pleos Connect SDK (only in the "pleos" variant)
    "pleosImplementation"("ai.pleos.playground:Vehicle:2.0.3")
    "pleosImplementation"("ai.pleos.playground:NaviHelper:2.0.3")
}
