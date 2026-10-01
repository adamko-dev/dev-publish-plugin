package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestCaseOrder
import io.kotest.core.test.TestScope
import org.gradle.testkit.runner.TaskOutcome.SKIPPED
import org.gradle.testkit.runner.TaskOutcome.SUCCESS

class IncrementalBuildTest : FunSpec({

  context("check incremental build") {
    val project = project()

    context("initial clean run") {
      test("1st time - publish task should run successfully") {
        project.runner.withArguments(
          ":clean",
          ":updateDevRepo",
        ).build {
          shouldHaveTaskWithOutcome(":publishMavenJavaPublicationToDevPublishMavenRepository", SUCCESS)
        }
      }

      test("2nd time - publish task should be UP_TO_DATE") {
        project.runner
          .withArguments(":updateDevRepo", "--info")
          .forwardOutput()
          .build {
            shouldHaveTaskWithOutcome(":publishMavenJavaPublicationToDevPublishMavenRepository", SKIPPED)
          }
      }
    }

    context("when dependency added") {
      project.buildGradleKts = project.buildGradleKts.replace("//<dep1> ", "/*<dep1>*/")

      test("1st time - publish task should run successfully") {
        project.runner
          .withArguments(":updateDevRepo", "--info")
          .forwardOutput()
          .build {
            shouldHaveTaskWithOutcome(":publishMavenJavaPublicationToDevPublishMavenRepository", SUCCESS)
          }
      }

      test("2nd time - publish task should be UP_TO_DATE") {
        project.runner
          .withArguments(":updateDevRepo", "--info")
          .forwardOutput()
          .build {
            shouldHaveTaskWithOutcome(":publishMavenJavaPublicationToDevPublishMavenRepository", SKIPPED)
          }
      }
    }

    context("when dependency changes") {
      project.buildGradleKts = project.buildGradleKts
        .replace("/*<dep1>*/", "//<dep1> ")
        .replace("//<dep2> ", "/*<dep2>*/")

      test("1st time - publish task should run successfully") {
        project.runner
          .withArguments(":updateDevRepo", "--info")
          .forwardOutput()
          .build {
            shouldHaveTaskWithOutcome(":publishMavenJavaPublicationToDevPublishMavenRepository", SUCCESS)
          }
      }

      test("2nd time - publish task should be UP_TO_DATE") {
        project.runner
          .withArguments(":updateDevRepo", "--info")
          .forwardOutput()
          .build {
            shouldHaveTaskWithOutcome(":publishMavenJavaPublicationToDevPublishMavenRepository", SKIPPED)
          }
      }
    }

    context("when dependency removed - expect updateDevRepo re-runs") {
      project.buildGradleKts = project.buildGradleKts
        .replace("/*<dep2>*/", "//<dep2> ")

      test("1st time - publish task should run successfully") {
        project.runner
          .withArguments(":updateDevRepo", "--info")
          .forwardOutput()
          .build {
            shouldHaveTaskWithOutcome(":publishMavenJavaPublicationToDevPublishMavenRepository", SUCCESS)
          }
      }

      test("2nd time - publish task should be UP_TO_DATE") {
        project.runner
          .withArguments(":updateDevRepo", "--info")
          .forwardOutput()
          .build {
            shouldHaveTaskWithOutcome(":publishMavenJavaPublicationToDevPublishMavenRepository", SKIPPED)
          }
      }
    }
  }
}) {

  override fun testCaseOrder(): TestCaseOrder = TestCaseOrder.Sequential

  companion object {

    private fun TestScope.project(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "single-module-project",
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
          |dependencies {
          |  //devPublication(project(":"))
          |}
          |
          |publishing {
          |  publications {
          |    create<MavenPublication>("mavenJava") {
          |      from(components["java"])
          |    }
          |  }
          |}
          |
          |dependencies {
          |  //<dep1> implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.0")
          |  //<dep2> implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
          |}
          |""".trimMargin()

        gradleProperties = """
          |org.gradle.jvmargs=-Dfile.encoding=UTF-8
          |org.gradle.caching=false
          |org.gradle.configuration-cache=true
          |org.gradle.logging.level=info
          |org.gradle.logging.stacktrace=full
          |org.gradle.parallel=true
          |""".trimMargin()
      }
  }
}
