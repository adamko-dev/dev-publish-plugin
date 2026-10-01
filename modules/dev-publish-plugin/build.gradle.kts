@file:Suppress("UnstableApiUsage")

import buildsrc.utils.skipTestFixturesPublications
import org.gradle.plugin.compatibility.compatibility
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
  buildsrc.conventions.`kotlin-gradle-plugin`
  `java-test-fixtures`
}

dependencies {
  implementation(projects.modules.devPublishCommon)

  testFixturesImplementation(projects.modules.devPublishCommon)

  testFixturesApi(gradleTestKit())
  testFixturesApi(platform(libs.kotest.bom))
  testFixturesApi(libs.kotest.runnerJUnit5)
  testFixturesApi(libs.kotest.assertionsCore)
}

kotlin {
  compilerOptions {
    optIn.addAll(
      "kotlin.io.path.ExperimentalPathApi",
      "dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi",
    )
  }
  @OptIn(ExperimentalAbiValidation::class)
  abiValidation {
    filters {
      exclude {
        annotatedWith.add("dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi")
      }
    }
  }
}

gradlePlugin {
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
    compatibility {
      features {
        isolatedProjects = true
        configurationCache = true
      }
    }
  }
}

val testMavenRepoDir: Directory = isolated.rootProject.projectDirectory.dir("build/test-maven-repo")
val projectTestTempDir: Provider<Directory> = layout.buildDirectory.dir("project-tests")

publishing {
  repositories {
    maven(testMavenRepoDir) {
      name = "TestMavenRepo"
    }
  }
}

skipTestFixturesPublications()

tasks.withType<Test>().configureEach {
  dependsOn("publishAllPublicationsToTestMavenRepoRepository")
  dependsOn(":modules:dev-publish-common:publishAllPublicationsToTestMavenRepoRepository")
  systemProperty("testMavenRepoDir", testMavenRepoDir.asFile.invariantSeparatorsPath)
  systemProperty("projectTestTempDir", projectTestTempDir.get().asFile.invariantSeparatorsPath)
}
