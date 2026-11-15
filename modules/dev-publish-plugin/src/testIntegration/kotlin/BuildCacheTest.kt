package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.paths.shouldBeADirectory
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.io.path.PathWalkOption.INCLUDE_DIRECTORIES
import kotlin.time.Duration.Companion.seconds
import org.gradle.testkit.runner.TaskOutcome.*

class BuildCacheTest : FunSpec({

  context("test single-module project") {
    val project = project()

    val expectedBuildCacheDir = project.projectDir.resolve("local-cache")

    project.runner
      .withArguments(
        "clean",
        ":build",
      ).build {

        test("can build") {
          expectedBuildCacheDir.shouldBeADirectory()

          shouldHaveRunTask(":compileKotlin")
          shouldHaveRunTask(":compileTestKotlin")

          shouldNotHaveRunTask(":publishMavenJavaPublicationToDevPublishMavenRepository")
          shouldNotHaveRunTask(":publishAllPublicationsToDevPublishMavenRepository")
          shouldNotHaveRunTask(":updateDevRepo")
        }

        val initialBuildCacheSize = expectedBuildCacheDir.recursiveFileSize()

        project.runner
          .withArguments(
            ":updateDevRepo",
          ).build {

            test("expect updateDevRepo runs the dev publishing tasks") {
              shouldHaveRunTask(":publishMavenJavaPublicationToDevPublishMavenRepository")
              shouldHaveRunTask(":publishAllPublicationsToDevPublishMavenRepository")
              shouldHaveRunTask(":updateDevRepo")
            }

            // dev publishing must not write into the build cache at all
            test("expect the build cache to be the same size afterwards") {
              expectedBuildCacheDir.shouldBeADirectory()

              val buildCacheSizeAfterDevPublish = expectedBuildCacheDir.recursiveFileSize()

              withClue("before: $initialBuildCacheSize, after: $buildCacheSizeAfterDevPublish") {
                buildCacheSizeAfterDevPublish shouldBe initialBuildCacheSize
              }
            }
          }
      }
  }

  context("when the dev repo is rebuilt with the same contents") {
    val project = project()

    test("populate generateDevPublishMetadata in build cache") {
      project.runner
        .withArguments(":generateDevPublishMetadata")
        .build {
          shouldHaveTaskWithAnyOutcome(":generateDevPublishMetadata", SUCCESS, FROM_CACHE, UP_TO_DATE)
        }
    }
    test("expect generateDevPublishMetadata is loaded from build cache") {
      project.runner
        .withArguments("clean", ":generateDevPublishMetadata")
        .build {
          shouldHaveTaskWithOutcome(":generateDevPublishMetadata", FROM_CACHE)
        }
    }
  }

  context("when a SNAPSHOT dev repo is rebuilt with the same contents") {
    val project = project(version = "0.0.1-SNAPSHOT")

    test("populate generateDevPublishMetadata in build cache") {
      project.runner
        .withArguments(":generateDevPublishMetadata")
        .build {
          shouldHaveTaskWithAnyOutcome(":generateDevPublishMetadata", SUCCESS, FROM_CACHE, UP_TO_DATE)
        }
    }

    test("expect generateDevPublishMetadata is loaded from build cache") {
      val firstJarName = snapshotJarName(project)
      project.runner
        .withArguments(
          "clean",
          ":generateDevPublishMetadata",
          // shift Gradle's clock, so the SNAPSHOT file names get a different timestamp
          "-Dorg.gradle.internal.test.clockoffset=${60.seconds.inWholeMilliseconds}",
        )
        .build {
          // without a new timestamp, this test proves nothing
          snapshotJarName(project) shouldNotBe firstJarName
          shouldHaveTaskWithOutcome(":generateDevPublishMetadata", FROM_CACHE)
        }
    }
  }
}) {

  companion object {
    private fun Path.recursiveFileSize(): Long =
      walk().filter { it.isRegularFile() }.sumOf { it.fileSize() }

    private fun snapshotJarName(project: GradleProjectTest): String =
      project.projectDir
        .resolve("build")
        .walk(INCLUDE_DIRECTORIES)
        .filter { it.isDirectory() && it.name.endsWith("-SNAPSHOT") }
        .flatMap { it.listDirectoryEntries("*.jar") }
        .firstOrNull()
        ?.name
        ?: error("No snapshot jar found")

    private fun TestScope.project(version: String = "0.0.1"): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "build-cache",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {

        buildGradleKts = """
          |plugins {
          |  kotlin("jvm") version embeddedKotlinVersion
          |  id("dev.adamko.dev-publish") version "+"
          |  `maven-publish`
          |}
          |
          |group = "foo.project"
          |version = "$version"
          |
          |publishing {
          |  publications {
          |    create<MavenPublication>("mavenJava") {
          |      from(components["java"])
          |    }
          |  }
          |}
          |
          |// Gradle 9+ archives are reproducible by default
          |if (GradleVersion.current() < GradleVersion.version("9.0.0")) {
          |  tasks.withType<AbstractArchiveTask>().configureEach {
          |    isPreserveFileTimestamps = false
          |    isReproducibleFileOrder = true
          |  }
          |}
          |""".trimMargin()

        settingsGradleKts += """
          |  
          |buildCache {
          |  local {
          |    directory = file("local-cache")
          |  }
          |}
          |""".trimMargin()

        createKotlinFile(
          "src/main/kotlin/FooClass.kt", """
            |class FooClass {
            |  fun name() = "FooClass"
            |}
            |""".trimMargin()
        )
      }
  }
}
