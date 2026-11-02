// Same Android Gradle Plugin family as the existing native7 reference project.
plugins { id("com.android.application") version "9.4.1" apply false; id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false; id("com.google.devtools.ksp") version "2.3.10" apply false }
tasks.register<Delete>("clean") { delete(layout.buildDirectory) }
