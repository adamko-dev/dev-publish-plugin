package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import dev.adamko.gradle.dev_publish.test_utils.GradleProjectTest.Companion.testedGradleVersion
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import kotlin.io.path.Path
import kotlin.io.path.pathString
import org.gradle.testkit.runner.TaskOutcome.*

/**
 * Verify that a Java-only project can use `devPublish.dependency()` and `dev-publish-utils`.
 */
class JavaConsumerTest : FunSpec({

  context("a Java-only project that declares devPublish.dependency") {
    val project = project()

    test("expect the tests run, and the Kotlin helpers work from Java") {
      project.runner
        .withArguments(
          ":test",
        )
        .forwardOutput()
        .build {
          shouldHaveTaskWithAnyOutcome(":test", SUCCESS, FROM_CACHE, UP_TO_DATE)
          shouldHaveRunTask(":updateDevRepo")
        }
    }

    test("expect dev-publish-utils resolves as a real module, on the runtime classpath") {
      project.runner
        .withArguments(
          ":dependencyInsight",
          "--configuration", "testRuntimeClasspath",
          "--dependency", "dev-publish-utils",
          "--quiet",
        )
        .build {

          val dependencyInsightReport =
            output
              .normalizeVersion()
              .substringBefore("(*) - Indicates repeated occurrences")
              .lines()
              // Drop `org.gradle.status`. Gradle derives it from the dev-publish-plugin version, so it's not stable.
              .filterNot { "| org.gradle.status" in it }
              // Drop JVM version - it can change per machine.
              .filterNot { "| org.gradle.jvm.version" in it }
              .joinToString("\n")
              .trim()

          val rootProjectName =
            if (testedGradleVersion >= "9.0") {
              "root project 'java-consumer'"
            } else {
              "root project :"
            }

          dependencyInsightReport shouldBe """
            |dev.adamko.gradle:dev-publish-utils:{version}
            |  Variant runtimeElements:
            |    | Attribute Name                     | Provided     | Requested    |
            |    |------------------------------------|--------------|--------------|
            |    | org.jetbrains.kotlin.platform.type | jvm          |              |
            |    | org.gradle.category                | library      | library      |
            |    | org.gradle.dependency.bundling     | external     | external     |
            |    | org.gradle.jvm.environment         | standard-jvm | standard-jvm |
            |    | org.gradle.libraryelements         | jar          | jar          |
            |    | org.gradle.usage                   | java-runtime | java-runtime |
            |
            |dev.adamko.gradle:dev-publish-utils:{version}
            |\--- $rootProjectName
            |     \--- $rootProjectName (*)
            """.trimMargin()
        }
    }

    test("expect devPublishElements carries a project-specific capability") {
      project.runner
        .withArguments(
          ":outgoingVariants",
          "--variant", "devPublishElements",
          "--quiet",
        )
        .build {
          output.trim().normalizeVersion() shouldBe """
            |--------------------------------------------------
            |Variant devPublishElements
            |--------------------------------------------------
            |The dev Maven repository location, and the library that reads it.
            |
            |Capabilities
            |    - dev.adamko.dev-publish.foo.project:java-consumer-dev-publish:{version}
            |Attributes
            |    - dev.adamko.gradle.dev_publish.type = dev-maven-repo-metadata
            |Artifacts
            |    - ${Path("build/tmp/.maven-dev/metadata").pathString}
            """.trimMargin()
        }
    }
  }
}) {

  companion object {

    private fun TestScope.project(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "java-consumer",
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
            |
            |dependencies {
            |  testImplementation(devPublish.dependency())
            |  testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
            |  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
            |}
            |
            |tasks.test {
            |  useJUnitPlatform()
            |}
            |""".trimMargin()

        createJavaFile(
          "src/main/java/com/example/Main.java",
          """
            |package com.example;
            |
            |public class Main {
            |  public static String hello() {
            |    return "hello";
            |  }
            |}
            |""".trimMargin()
        )

        createJavaFile(
          "src/test/java/com/example/JavaConsumerTest.java",
          """
            |package com.example;
            |
            |import static org.junit.jupiter.api.Assertions.assertTrue;
            |
            |import dev.adamko.gradle.dev_publish.DevPublishTestKit;
            |import java.nio.file.Files;
            |import java.nio.file.Path;
            |import org.junit.jupiter.api.Test;
            |
            |class JavaConsumerTest {
            |
            |  @Test
            |  void devMavenRepoIsCallableFromJava() {
            |    Path repo = DevPublishTestKit.devMavenRepo();
            |
            |    Path jar = repo.resolve("foo/project/java-consumer/0.0.1/java-consumer-0.0.1.jar");
            |    assertTrue(Files.exists(jar), "expected this project's own publication at " + jar);
            |  }
            |
            |  @Test
            |  void settingsHelpersAreCallableFromJava() {
            |    String settings = DevPublishTestKit.devPublishSettings("consumer");
            |
            |    assertTrue(settings.contains("pluginManagement"), settings);
            |  }
            |}
            |""".trimMargin()
        )
      }

    private val devPublishVersionRegex = Regex(
      """(?<=[:\-]dev-publish(?:-utils)?:)\S+"""
    )

    /**
     * Replace versions of dev-publish dependencies with `{version}`, for stable test assertions.
     */
    private fun String.normalizeVersion(): String =
      lines().joinToString("\n") { line ->
        line.replace(devPublishVersionRegex, "{version}")
      }
  }
}
