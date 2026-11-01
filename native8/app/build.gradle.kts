plugins { id("com.android.application") }
android {
    namespace = "com.alsafa.native8"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.alsafa.native8"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "8.0.0-import-preview"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes { release { isMinifyEnabled = false } }
}

dependencies {
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.zxing:core:3.5.3")
}
