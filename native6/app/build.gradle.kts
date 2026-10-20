plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.alsafa.native6"
    compileSdk = 34
    defaultConfig { applicationId = "com.alsafa.native6"; minSdk = 26; targetSdk = 34; versionCode = 1; versionName = "6.0.0-dev01"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { testImplementation("junit:junit:4.13.2"); testImplementation("org.robolectric:robolectric:4.14.1") }
android.testOptions.unitTests.isIncludeAndroidResources = true

tasks.withType<Test>().configureEach {
    listOf("https.proxyHost", "https.proxyPort", "http.proxyHost", "http.proxyPort", "javax.net.ssl.trustStore", "javax.net.ssl.trustStorePassword").forEach { key -> System.getProperty(key)?.let { systemProperty(key, it) } }
}
