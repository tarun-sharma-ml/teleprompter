plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.telepromptertg"
    //noinspection GradleDependency
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.tarunsharmaml.teleprompter"
        minSdk = 26
        //noinspection OldTargetApi
        targetSdk = 34
        versionCode = 5
        versionName = "1.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
