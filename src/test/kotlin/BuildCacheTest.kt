package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.paths.shouldBeADirectory
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.*

@OptIn(ExperimentalPathApi::class)
class BuildCacheTest : FunSpec({

  context("test single-module project") {
    val project = project()

    val expectedBuildCacheDir = project.projectDir.resolve("local-cache")
    expectedBuildCacheDir.deleteRecursively()

    project.runner
      //.forwardOutput()
      .withArguments(
        ":build",
        "--stacktrace",
        "--configuration-cache",
        "--build-cache",
        //"-Dorg.gradle.caching.debug=true",
        "--rerun-tasks",
      ).build {

        test("can build") {
          expectedBuildCacheDir.shouldBeADirectory()

          shouldHaveRunTask(":compileKotlin")
          shouldHaveRunTask(":compileTestKotlin")

          shouldNotHaveRunTask(":publishMavenJavaPublicationToDevPublishMavenRepository")
          shouldNotHaveRunTask(":publishAllPublicationsToDevPublishMavenRepository")
          shouldNotHaveRunTask(":updateDevRepo")
        }

        val initialBuildCacheSize =
          expectedBuildCacheDir.walk().filter { it.isRegularFile() }.map { it.fileSize() }.sum()

        project.runner
          //.forwardOutput()
          .withArguments(
            ":updateDevRepo",
            "--stacktrace",
            "--configuration-cache",
            "--build-cache",
            //"-Dorg.gradle.caching.debug=true",
          ).build {

            test("build cache should be same size") {
              shouldHaveRunTask(":publishMavenJavaPublicationToDevPublishMavenRepository")
              shouldHaveRunTask(":publishAllPublicationsToDevPublishMavenRepository")
              shouldHaveRunTask(":updateDevRepo")
            }

            test("Build cache size should be the same") {
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
      walk().filter { it.isRegularFile() }.map { it.fileSize() }.sum()

    private fun TestScope.project(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "build-cache",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {

        buildGradleKts = """
            plugins {
              kotlin("jvm") version embeddedKotlinVersion
              id("dev.adamko.dev-publish") version "+"
              `maven-publish`
            }
            
            group = "foo.project"
            version = "0.0.1"
            
            publishing {
              publications {
                create<MavenPublication>("mavenJava") {
                  from(components["java"])
                }
              }
            }
        """.trimIndent()

        settingsGradleKts += """
          
        buildCache {
            local {
                directory = file("local-cache").toURI()
            }
        }
        """.trimIndent()

        createKotlinFile(
          "src/main/kotlin/FooClass.kt", """
              class FooClass {
                fun name() = "FooClass"
              }
            """.trimIndent()
        )
      }
  }
}
