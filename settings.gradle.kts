pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  @Suppress("UnstableApiUsage")
  repositories {
    google()
    mavenCentral()
  }
}

include(":library")

rootProject.name = "fake-system-ui"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
