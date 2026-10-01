rootProject.name = "buildSrc"

pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://central.sonatype.com/repository/maven-snapshots/") {
      mavenContent { snapshotsOnly() }
    }
  }
}

plugins {
  id("dev.adamko.gradle-kotlin-accessors") version "main-SNAPSHOT"
}
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

  repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://central.sonatype.com/repository/maven-snapshots/") {
      mavenContent { snapshotsOnly() }
    }
  }

  versionCatalogs {
    create("libs") {
      from(files("../gradle/libs.versions.toml"))
    }
  }
}

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
