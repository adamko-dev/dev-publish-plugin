@file:Suppress("UnstableApiUsage")

import buildsrc.utils.TestDirsArgument.Companion.TestDirsArgument
import buildsrc.utils.skipTestFixturesPublications
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.gradle.plugin.compatibility.compatibility
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
  buildsrc.conventions.`kotlin-gradle-plugin`
  buildsrc.conventions.`dev-publish-bootstrap`
  `java-test-fixtures`
  id("dev.adamko.dev-publish")
}

dependencies {
  implementation(projects.modules.devPublishCommon)

  devPublication(project())
  devPublication(projects.modules.devPublishCommon)
  devPublication(projects.modules.devPublishUtils)

  devPublishUtils(devPublishBootstrap.bootstrapDevPublishJars)
  testFixturesImplementation(devPublishBootstrap.bootstrapDevPublishJars)

  testFixturesApi(gradleTestKit())
  testFixturesApi(platform(libs.kotest.bom))
  testFixturesApi(libs.kotest.runnerJUnit5)
  testFixturesApi(libs.kotest.assertionsCore)
}

devPublishBootstrap.modules = setOf(
  "dev-publish-common",
  "dev-publish-utils",
  // exclude the plugin itself, so it doesn't shadow this project's classes in tests
  //"dev-publish-plugin",
)

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
    displayName = "Dev Publish"
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

val projectTestTempDir: Provider<Directory> = layout.buildDirectory.dir("project-tests")

/** The `examples/` projects. */
val exampleFiles: FileCollection =
  isolated.rootProject.projectDirectory.dir("examples").asFileTree

/** The version being built, so a test can check the version that `examples/` pins. */
val devPublishVersion: Provider<String> = provider { project.version.toString() }

skipTestFixturesPublications()

testing {
  suites.withType<JvmTestSuite>().configureEach {
    useJUnitJupiter()
  }
  val testIntegration by suites.registering(JvmTestSuite::class) {
    dependencies {
      implementation(testFixtures(project()))
      implementation(devPublish.dependency())
    }
    targets.configureEach {
      testTask.configure {
        inputs.files(exampleFiles)
          .withPropertyName("exampleFiles")
          .withPathSensitivity(PathSensitivity.RELATIVE)
        val projectTestTemp = projectTestTempDir
        val gradleUserHome = gradle.gradleUserHomeDir
        jvmArgumentProviders.add(
          objects.TestDirsArgument {
            projectTestTempDir = projectTestTemp
            hostGradleUserHome.fileValue(gradleUserHome)
          }
        )
        systemProperty("testedGradleVersion", GradleVersion.current().version)
      }
    }

    val additionalTestedGradleVersions = listOf(
      "8.14.6",
    )
    additionalTestedGradleVersions.forEach { testedGradleVersion ->
      targets.register("testIntegrationGradle_${testedGradleVersion.replace(Regex("[^\\d]"), "_")}") {
        testTask.configure {
          shouldRunAfter("test")
          systemProperty("testedGradleVersion", testedGradleVersion)
        }
      }
    }
  }
  val testDocs by suites.registering(JvmTestSuite::class) {
    dependencies {
      implementation(testFixtures(project()))
    }
    targets.configureEach {
      testTask.configure {
        description = "Checks the docs."
        inputs.files(isolated.rootProject.projectDirectory.asFileTree.matching {
          include("**/*.md")
          exclude("**/build/**", "**/.gradle/**", "**/.git/**")
        })
          .withPropertyName("docsFiles")
          .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(exampleFiles)
          .withPropertyName("exampleFiles")
          .withPathSensitivity(PathSensitivity.RELATIVE)
      }
    }
  }
  tasks.check {
    dependsOn(testIntegration, testDocs)
  }
}

val generateDevPublishVersionKt by tasks.registering {
  val outputDir = temporaryDir.toPath()
  outputs.dir(outputDir)

  val projectVersion = provider { project.version.toString() }
  inputs.property("version", projectVersion)
  doLast {
    outputDir
      .resolve("dev/adamko/gradle/dev_publish/internal")
      .createDirectories()
      .resolve("DevPublishVersion.kt")
      .writeText(
        """
        |// Do not edit: generated by $path
        |package dev.adamko.gradle.dev_publish.internal
        |
        |/** The current version of the Dev Publish plugin. */
        |internal const val DevPublishVersion = "${projectVersion.get()}"
        |""".trimMargin()
      )
  }
}

kotlin.sourceSets.main {
  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  generatedKotlin.srcDir(generateDevPublishVersionKt)
}
