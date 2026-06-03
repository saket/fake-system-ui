pluginManagement {
  repositories {
    mavenLocal()
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  @Suppress("UnstableApiUsage")
  repositories {
    mavenLocal()
    google()
    mavenCentral()
  }
}

include(":library")

rootProject.name = "fake-system-ui"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
