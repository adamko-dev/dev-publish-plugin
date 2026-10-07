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
    exclusiveContent {
      forRepository {
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
          name = "MavenCentralSnapshotsDevPublishBootstrap"
        }
      }
      filter {
        includeModule("dev.adamko.gradle", "dev-publish-common")
        includeModule("dev.adamko.gradle", "dev-publish-utils")
      }
    }
  }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

include(
  ":modules:dev-publish-common",
  ":modules:dev-publish-plugin",
  ":modules:dev-publish-utils",
)
