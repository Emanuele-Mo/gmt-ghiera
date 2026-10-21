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
        versionCode = 1
        versionName = "0.1-test"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
}
