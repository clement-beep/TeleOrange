plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.clement.teleorange"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.clement.teleorange"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signé avec la clé debug pour pouvoir l'installer directement sur ton téléphone
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Aucune dépendance externe : pas de pub, pas de tracker, rien.
