package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope

/**
 * Dev Publish works in a project that doesn't apply `maven-publish`.
 */
class WithoutMavenPublishTest : FunSpec({

  context("a project without maven-publish") {
    val project = project()

    test("expect updateDevRepo runs") {
      project.runner.withArguments(
        ":updateDevRepo",
        "--stacktrace",
      ).build {
        shouldHaveRunTask(":publishAllToDevRepo")
        shouldHaveRunTask(":updateDevRepo")
      }
    }
  }
}) {
  companion object {
    private fun TestScope.project(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "without-maven-publish",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {
        buildGradleKts = """
          |plugins {
          |  id("dev.adamko.dev-publish") version "+"
          |}
          |""".trimMargin()
      }
  }
}
