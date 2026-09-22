rootProject.name = "dev-publish-plugin"

pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
  repositoriesMode = RepositoriesMode.PREFER_SETTINGS

  repositories {
    mavenCentral()
  }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
  ":modules:dev-publish-common",
  ":modules:dev-publish-plugin",
  ":modules:dev-publish-utils",
)
