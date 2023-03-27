rootProject.name = "dev-publish-plugin"

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

  repositories {
    mavenCentral()
    gradlePluginPortal()

    ivy("https://services.gradle.org/") {
      name = "Gradle Services"
      patternLayout {
        // https://services.gradle.org/distributions/gradle-8.0.2-bin.zip
        artifact("[organisation]/[module]-[revision]-bin.zip")
      }
      metadataSources { artifact() }
    }
  }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
