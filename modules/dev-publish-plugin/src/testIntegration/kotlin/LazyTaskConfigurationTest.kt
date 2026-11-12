package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope

/**
 * Dev Publish must not realise unrelated tasks.
 */
class LazyTaskConfigurationTest : FunSpec({

  context("a project with a task that fails when configured") {
    val project = project()

    test("expect publishAllToDevRepo runs without configuring the task") {
      project.runner.withArguments(
        ":publishAllToDevRepo",
        "--stacktrace",
      ).build {
        shouldHaveRunTask(":publishMavenJavaPublicationToDevPublishMavenRepository")
        shouldHaveRunTask(":publishAllToDevRepo")
      }
    }

    test("expect updateDevRepo runs without configuring the task") {
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
        projectName = "lazy-task-configuration",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {
        buildGradleKts = """
          |plugins {
          |  java
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
          |
          |tasks.register("unconfigurable") {
          |  error("unconfigurable must not be configured")
          |}
          |""".trimMargin()
      }
  }
}
