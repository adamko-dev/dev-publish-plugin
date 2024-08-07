import buildsrc.utils.excludeProjectConfigurationDirs
import buildsrc.utils.skipTestFixturesPublications
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  buildsrc.conventions.`kotlin-gradle-plugin`
  dev.adamko.kotlin.`binary-compatibility-validator`
  `java-test-fixtures`
  idea
}

project.version = "0.5.0-SNAPSHOT"
project.group = "dev.adamko.gradle"

dependencies {
  testFixturesApi(gradleTestKit())
  testFixturesApi(platform(libs.kotest.bom))
  testFixturesApi(libs.kotest.runnerJUnit5)
  testFixturesApi(libs.kotest.assertionsCore)
}


@Suppress("UnstableApiUsage")
gradlePlugin {
  isAutomatedPublishing = true
  website = "https://github.com/adamko-dev/dev-publish-plugin"
  vcsUrl = "https://github.com/adamko-dev/dev-publish-plugin.git"

  plugins.register("DevPublish") {
    id = "dev.adamko.dev-publish"
    displayName = "DevPublish"
    description = "Publish Gradle Projects to a project-local repository, for functional testing"
    implementationClass = "dev.adamko.gradle.dev_publish.DevPublishPlugin"
    tags.addAll(
      "maven",
      "publishing",
      "maven-publish",
      "test",
      "verify",
      "check",
      "functional-test",
      "integration-test",
      "publication",
    )
  }
}

val testMavenRepoDir = layout.buildDirectory.dir("test-maven-repo")
val projectTestTempDir = layout.buildDirectory.dir("project-tests")

publishing {
  repositories {
    maven(testMavenRepoDir) {
      name = "TestMavenRepo"
    }
  }
}

binaryCompatibilityValidator {
  ignoredMarkers.addAll(
    "dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi",
  )
}

skipTestFixturesPublications()

tasks.withType<Test>().configureEach {
  dependsOn("publishAllPublicationsToTestMavenRepoRepository")
  systemProperty("testMavenRepoDir", testMavenRepoDir.get().asFile.invariantSeparatorsPath)
  systemProperty("projectTestTempDir", projectTestTempDir.get().asFile.invariantSeparatorsPath)
  // Remove Kotest autoscan.disable when Kotest version >= 6.0
  systemProperty("kotest.framework.classpath.scanning.autoscan.disable", true)
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    optIn.addAll(
      "dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi",
    )
  }
}

idea {
  module {
    excludeProjectConfigurationDirs(layout, providers)
    excludeDirs.addAll(
      layout.files(
        ".idea",
        "gradle/wrapper",
      )
    )
  }
}
