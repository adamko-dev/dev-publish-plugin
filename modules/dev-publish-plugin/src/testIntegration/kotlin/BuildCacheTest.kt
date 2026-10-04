package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.paths.shouldBeADirectory
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.*

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
}) {

  companion object {
    private fun Path.recursiveFileSize(): Long =
      walk().filter { it.isRegularFile() }.sumOf { it.fileSize() }

    private fun TestScope.project(): GradleProjectTest =
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
          |version = "0.0.1"
          |
          |publishing {
          |  publications {
          |    create<MavenPublication>("mavenJava") {
          |      from(components["java"])
          |    }
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
