package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import dev.adamko.gradle.dev_publish.test_utils.GradleProjectTest.Companion.testedGradleVersion
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * `generatePublicationHashTask` is no longer used.
 */
class DeprecatedTaskTest : FunSpec({

  context("when the deprecated task is used") {
    val project = project()

    test("expect the task still runs, and the deprecation is reported") {
      project.runner
        // the deprecation is only reported when the task executes, not when it's up-to-date
        .withArguments(":generatePublicationHashTask", "--rerun", "--warning-mode=all")
        .forwardOutput()
        .build {
          shouldHaveRunTask(":generatePublicationHashTask")

          if (testedGradleVersion >= "9.0") {
            output shouldContain "Task 'generatePublicationHashTask', in root project 'deprecated-task', is deprecated. It will be removed in DevPublish version 2.0."
          }
        }
    }

    test("expect the deprecation is also reported as a structured problem") {
      val reportFile = project.projectDir.resolve("build/reports/problems/problems-report.html")
      val reportContent = reportFile.readText()

      withClue("the Problems API entry should be in ${reportFile.name}") {
        reportContent shouldContain "deprecated-task"
        reportContent shouldContain "DevPublish"
        reportContent shouldContain "is deprecated"
      }
    }

    test("expect the task is hidden from the task list") {
      project.runner.withArguments(":tasks").build {
        output shouldNotContain "generatePublicationHashTask"
      }
    }
  }
}) {

  companion object {

    private fun TestScope.project(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "deprecated-task",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {
        buildGradleKts = """
            |plugins {
            |  `java-library`
            |  `maven-publish`
            |  id("dev.adamko.dev-publish") version "+"
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
      }
  }
}
