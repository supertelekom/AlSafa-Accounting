// Same Android Gradle Plugin family as the existing native7 reference project.
plugins { id("com.android.application") version "9.4.1" apply false }
tasks.register<Delete>("clean") { delete(layout.buildDirectory) }
