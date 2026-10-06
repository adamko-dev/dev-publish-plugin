package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import kotlin.io.path.readText
import org.gradle.testkit.runner.TaskOutcome.*
import org.gradle.util.GradleVersion

/**
 * Use the dev Maven repo as the input of a task that isn't a JVM test.
 */
class DevRepoTaskInputTest : FunSpec({

  /** Gradle 8 doesn't make reproducible archives by default, so the checksum changes on every publish. */
  val isGradle9 = GradleProjectTest.testedGradleVersion >= GradleVersion.version("9.0")

  context("a task that lists the dev repo, using the dev repo checksum").config(enabled = isGradle9) {
    val project = project(consumerBuildGradleKts = ListDevRepoFromChecksum)

    val listing = project.projectDir.resolve("consumer/build/dev-repo-listing.txt")

    test("expect the checksum input runs updateDevRepo first") {
      project.runner.withArguments(
        ":consumer:listDevRepo",
      ).build {
        // devRepo is location-only, so the dependency comes from the checksum
        shouldHaveRunTask(":consumer:generateDevPublishMetadata")
        shouldHaveRunTask(":consumer:updateDevRepo")
        listing.readText() shouldBe ExpectedListing
      }
    }

    test("expect the task is up-to-date when nothing changed") {
      project.runner.withArguments(
        ":consumer:listDevRepo",
      ).build {
        shouldHaveTaskWithOutcome(":consumer:listDevRepo", UP_TO_DATE)
      }
    }

    test("expect the task re-runs when a publication changes") {
      project.writeLibClass(name = "com.example.lib, changed")

      project.runner.withArguments(
        ":consumer:listDevRepo",
      ).build {
        shouldHaveTaskWithOutcome(":lib:publishMavenJavaPublicationToDevPublishMavenRepository", SUCCESS)
        shouldHaveTaskWithAnyOutcome(":consumer:listDevRepo", SUCCESS, FROM_CACHE)
        listing.readText() shouldBe ExpectedListing
      }
    }

    test("expect the task is loaded from the build cache when the publication is reverted") {
      project.writeLibClass(name = "com.example.lib")

      project.runner.withArguments(
        ":consumer:listDevRepo",
      ).build {
        // republished, so maven-metadata.xml has a new timestamp...
        shouldHaveTaskWithOutcome(":lib:publishMavenJavaPublicationToDevPublishMavenRepository", SUCCESS)
        // ...but the checksum ignores it
        shouldHaveTaskWithOutcome(":consumer:listDevRepo", FROM_CACHE)
        listing.readText() shouldBe ExpectedListing
      }
    }
  }
}) {
  companion object {

    private fun GradleProjectTest.writeLibClass(name: String) {
      createKotlinFile(
        "lib/src/main/kotlin/LibClass.kt",
        """
          |package com.example.lib
          |
          |class LibClass {
          |  fun name() = "$name"
          |}
          |""".trimMargin()
      )
    }

    private fun TestScope.project(consumerBuildGradleKts: String) =
      gradleKtsProjectTest(
        projectName = "dev repo task input",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {

        settingsGradleKts += """
          |include(
          |  ":lib",
          |  ":consumer",
          |)
          |""".trimMargin()

        buildGradleKts = """
          |plugins {
          |  kotlin("jvm") version embeddedKotlinVersion apply false
          |}
          |""".trimMargin()

        dir("lib") {
          buildGradleKts = """
            |plugins {
            |  kotlin("jvm")
            |  `maven-publish`
            |  id("dev.adamko.dev-publish") version "+"
            |}
            |
            |group = "com.example"
            |version = "1.2.3"
            |
            |publishing {
            |  publications {
            |    create<MavenPublication>("mavenJava") {
            |      from(components["java"])
            |    }
            |  }
            |}
            |""".trimMargin()
        }

        writeLibClass(name = "com.example.lib")

        dir("consumer") {
          buildGradleKts = consumerBuildGradleKts
        }
      }

    private val ListDevRepoFromChecksum = /*language=kts*/ """
      |import kotlin.io.path.*
      |
      |plugins {
      |  id("dev.adamko.dev-publish") version "+"
      |}
      |
      |dependencies {
      |  devPublication(project(":lib"))
      |}
      |
      |/**
      | * Writes the path of every file in [devRepo] into [listing].
      | *
      | * [devRepo] isn't an input: [devRepoMetadata] tracks its contents.
      | */
      |@CacheableTask
      |abstract class ListDevRepo : DefaultTask() {
      |  @get:Internal
      |  abstract val devRepo: DirectoryProperty
      |
      |  @get:InputFiles
      |  @get:PathSensitive(PathSensitivity.NONE)
      |  abstract val devRepoMetadata: ConfigurableFileCollection
      |
      |  @get:OutputFile
      |  abstract val listing: RegularFileProperty
      |
      |  @TaskAction
      |  fun list() {
      |    val devRepo = devRepo.get().asFile.toPath()
      |    val paths = devRepo.walk()
      |      .filter { it.isRegularFile() }
      |      .map { it.relativeTo(devRepo).invariantSeparatorsPathString }
      |      .sorted()
      |      .joinToString("\n")
      |    listing.get().asFile.writeText(paths)
      |  }
      |}
      |
      |tasks.register<ListDevRepo>("listDevRepo") {
      |  // only the location: the checksum carries the dependency on updateDevRepo
      |  devRepo = tasks.updateDevRepo.flatMap { it.devRepo.locationOnly }
      |  devRepoMetadata.from(tasks.generateDevPublishMetadata)
      |  listing = layout.buildDirectory.file("dev-repo-listing.txt")
      |}
      |""".trimMargin()

    private val ExpectedListing = """
      com/example/lib/1.2.3/lib-1.2.3.jar
      com/example/lib/1.2.3/lib-1.2.3.jar.md5
      com/example/lib/1.2.3/lib-1.2.3.jar.sha1
      com/example/lib/1.2.3/lib-1.2.3.jar.sha256
      com/example/lib/1.2.3/lib-1.2.3.jar.sha512
      com/example/lib/1.2.3/lib-1.2.3.module
      com/example/lib/1.2.3/lib-1.2.3.module.md5
      com/example/lib/1.2.3/lib-1.2.3.module.sha1
      com/example/lib/1.2.3/lib-1.2.3.module.sha256
      com/example/lib/1.2.3/lib-1.2.3.module.sha512
      com/example/lib/1.2.3/lib-1.2.3.pom
      com/example/lib/1.2.3/lib-1.2.3.pom.md5
      com/example/lib/1.2.3/lib-1.2.3.pom.sha1
      com/example/lib/1.2.3/lib-1.2.3.pom.sha256
      com/example/lib/1.2.3/lib-1.2.3.pom.sha512
      com/example/lib/maven-metadata.xml
      com/example/lib/maven-metadata.xml.md5
      com/example/lib/maven-metadata.xml.sha1
      com/example/lib/maven-metadata.xml.sha256
      com/example/lib/maven-metadata.xml.sha512
      """.trimIndent()
  }
}
