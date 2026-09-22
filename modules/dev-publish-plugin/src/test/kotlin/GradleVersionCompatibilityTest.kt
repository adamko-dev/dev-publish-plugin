package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.datatest.withData
import io.kotest.matchers.string.shouldContain

/**
 * Verifies the Gradle versions that DevPublish claims to support in the README.
 *
 * Without this test the supported-version range is guesswork.
 */
class GradleVersionCompatibilityTest : FunSpec({

  val supportedGradleVersions = listOf(
    "8.14.5",
    "9.7.1",
  )

  withData(
    nameFn = { "Gradle $it" },
    supportedGradleVersions,
  ) { gradleVersion ->
    val project = project()

    project.runner
      .withGradleVersion(gradleVersion)
      .withArguments(":updateDevRepo")
      .forwardOutput()
      .build {
        output shouldContain "SUCCESSFUL"
      }
  }
}) {

  companion object {

    private fun TestScope.project(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "gradle-version-compatibility",
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

        createJavaFile(
          "src/main/java/FooClass.java",
          """
            |public class FooClass {
            |  public String name() { return "FooClass"; }
            |}
            |""".trimMargin()
        )
      }
  }
}
