pluginManagement {
  repositories {
    gradlePluginPortal()
  }
}

rootProject.name = "multi-project-aggregation"

dependencyResolutionManagement {
  repositories {
    mavenCentral()
  }
}

include(
  ":lib-core",
  ":lib-extras",
  ":signed-library",
  ":functional-tests",
)
