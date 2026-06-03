import org.gradle.kotlin.dsl.implementation

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.paparazzi)
  alias(libs.plugins.mavenPublish)
}

android {
  namespace = "me.saket.fakesystemui"

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()
    compileSdk = libs.versions.compileSdk.get().toInt()
    lint.abortOnError = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }
}

dependencies {
  api(libs.androidx.compose.foundation)
  api(libs.androidx.compose.ui.test.junit)
  api(libs.paparazzi)

  lintChecks(libs.composeLintChecks)
  implementation(libs.androidx.ktx)
  implementation(libs.androidx.lifecycle)
  implementation(libs.androidx.savedstate)
  implementation(libs.androidx.shapes)

  testImplementation(libs.assertk)
  testImplementation(libs.junit)
  testImplementation(libs.testParameterInjector)
}

// Used on CI to prevent publishing of non-snapshot versions.
tasks.register("throwIfVersionIsNotSnapshot") {
  doLast {
    val libraryVersion = properties["VERSION_NAME"] as String
    check(libraryVersion.endsWith("SNAPSHOT")) {
      "Project isn't using a snapshot version = $libraryVersion"
    }
  }
}
