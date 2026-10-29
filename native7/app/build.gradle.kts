plugins { id("com.android.application") }
android {
    namespace = "com.alsafa.native7"
    compileSdk = 37
    // Use the Build Tools version selected by this AGP release.
    defaultConfig { applicationId = "com.alsafa.native7"; minSdk = 26; targetSdk = 37; versionCode = 2; versionName = "7.0.1-ui-preview" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    // Built-in Kotlin inherits JVM 17 from compileOptions.
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}


// Embedded offline Code 128/EAN/UPC barcode reader; no external scanner app needed.
dependencies { implementation("com.journeyapps:zxing-android-embedded:4.3.0") }
