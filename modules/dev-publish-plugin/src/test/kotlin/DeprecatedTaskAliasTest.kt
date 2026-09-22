package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.string.shouldContain
import kotlin.io.path.readText

/**
 * `generatePublicationHashTask` was renamed to `generatePublicationChecksums`. The old name still
 * works, and reports the rename through the Problems API.
 */
class DeprecatedTaskAliasTest : FunSpec({

  context("when the deprecated task name is used") {
    val project = project()

    test("expect the task still runs, and the rename is reported") {
      project.runner
        .withArguments(":generatePublicationHashTask", "--warning-mode=all")
        .forwardOutput()
        .build {
          shouldHaveRunTask(":generatePublicationChecksums")

          output shouldContain "Task 'generatePublicationHashTask' was renamed to 'generatePublicationChecksums'"
        }
    }

    test("expect the rename is also reported as a structured problem") {
      val report = project.projectDir
        .resolve("build/reports/problems/problems-report.html")
        .readText()

      withClue("the Problems API entry should be in $report") {
        report shouldContain "deprecated-task"
        report shouldContain "DevPublish"
        report shouldContain "was renamed to"
      }
    }
  }
}) {

  companion object {

    private fun TestScope.project(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "deprecated-task-alias",
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
