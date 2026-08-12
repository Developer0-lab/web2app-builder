plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.web2app.generated"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.web2app.generated"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "WEB_APP_URL", "\"https://example.com\"")
    }

    buildFeatures {
        buildConfig = true
    }
}
