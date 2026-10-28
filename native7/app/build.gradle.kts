plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.alsafa.native7"
    compileSdk = 34
    defaultConfig { applicationId = "com.alsafa.native7"; minSdk = 26; targetSdk = 34; versionCode = 1; versionName = "7.0.0-ui-preview" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
