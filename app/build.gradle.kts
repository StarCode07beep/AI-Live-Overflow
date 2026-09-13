plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.starcode.ai_live_pet"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.starcode.xujin_deskpet"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.1-notify"
    }

    signingConfigs {
        create("fixed") {
            storeFile = rootProject.file("signing/xujin.p12")
            storePassword = "xujin0921"
            keyAlias = "xujin"
            keyPassword = "xujin0921"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixed")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
