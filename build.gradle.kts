import com.android.build.api.dsl.LibraryExtension as AndroidLibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
  repositories {
    google()
    mavenCentral()
  }
}

plugins {
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.paparazzi) apply false
  alias(libs.plugins.dokka) apply false
  alias(libs.plugins.mavenPublish) apply false
}

allprojects {
  plugins.withId("com.android.library") {
    extensions.configure<AndroidLibraryExtension>("android") {
      compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
      }
    }
  }
  tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_11)
    }
  }
}
