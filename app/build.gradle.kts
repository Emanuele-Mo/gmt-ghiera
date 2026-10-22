plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "it.emanuele.gmtbezel"
    compileSdk = 34
    defaultConfig {
        applicationId = "it.emanuele.gmtbezel"
        minSdk = 30
        targetSdk = 34
        versionCode = 10
        versionName = "1.0-ghiera-e-battito"
    }
    signingConfigs {
        // chiave fissa: tutte le build hanno la stessa firma e "adb install -r" funziona sempre
        getByName("debug") {
            storeFile = file("gmt-debug.keystore")
            storePassword = "android"
            keyAlias = "gmtdebug"
            keyPassword = "android"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.health:health-services-client:1.0.0-rc02")
    implementation("com.google.guava:guava:33.0.0-android")
}
