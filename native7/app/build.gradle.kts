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


// Bundled ML Kit barcode model: works offline without Play Services download.
dependencies { implementation("com.google.mlkit:barcode-scanning:17.3.0") }
