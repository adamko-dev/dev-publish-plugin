package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import dev.adamko.gradle.dev_publish.test_utils.GradleProjectTest.Companion.testedGradleVersion
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import org.gradle.util.GradleVersion

class DevPublicationDefaultDependencyTest : FunSpec({

  context("given a root project that collects publications") {
    val project = project()

    /** The `devPublication` dependencies the root project resolves. */
    fun devPublications(enabled: String): String =
      project.runner
        .withArguments(
          ":dependencies",
          "--configuration",
          "devPublicationResolvableElements",
          "-PdevPublishEnabled=$enabled",
        )
        .build()
        .output
        .lines()
        .dropWhile { !it.startsWith("devPublicationResolvableElements") }
        .takeWhile { it.isNotBlank() }
        .joinToString("\n")

    fun projectDisplayName(projectPath: String): String = if (testedGradleVersion < "9.0.0") {
      "project $projectPath"
    } else {
      "project '$projectPath'"
    }

    val projectLibA = projectDisplayName(":lib-a")
    val projectLibB = projectDisplayName(":lib-b")

    val rootProjectDisplayName = if (testedGradleVersion < "9.0.0") {
      "root project :"
    } else {
      "root project 'dev publication default dependency'"
    }

    test("expect no devPublication dependencies collects the root project") {
      devPublications(enabled = "") shouldBe """
        |devPublicationResolvableElements - Resolve dev Maven Publications.
        |\--- $rootProjectDisplayName (*)
        """.trimMargin()
    }

    test("expect declaring another devPublication dependency leaves out the root project") {
      devPublications(enabled = ":lib-a,:lib-b") shouldBe """
        |devPublicationResolvableElements - Resolve dev Maven Publications.
        |+--- $projectLibA
        ||    \--- $projectLibA (*)
        |\--- $projectLibB
        |     \--- $projectLibB (*)
        """.trimMargin()
    }

    test("expect declaring the root project collects it alongside other projects") {
      devPublications(enabled = ":lib-a,:") shouldBe """
        |devPublicationResolvableElements - Resolve dev Maven Publications.
        |+--- $projectLibA
        ||    \--- $projectLibA (*)
        |\--- $rootProjectDisplayName (*)
        """.trimMargin()
    }
  }
})

private fun TestScope.project(): GradleProjectTest =
  gradleKtsProjectTest(
    projectName = "dev publication default dependency",
    testProjectPath = testCase.descriptor.slashSeparatedPath(),
  ) {

    settingsGradleKts += """
      |include(
      |  ":lib-a",
      |  ":lib-b",
      |)
      |""".trimMargin()

    // `DependencyHandler.project()` is only available in Gradle 9.5+
    val rootDependency =
      if (testedGradleVersion >= GradleVersion.version("9.5.0")) "project()" else """project(":")"""

    buildGradleKts = """
      |plugins {
      |  base
      |  id("dev.adamko.dev-publish") version "+"
      |}
      |
      |fun isEnabled(projectPath: String): Boolean =
      |  projectPath in providers.gradleProperty("devPublishEnabled").get().split(",")
      |
      |dependencies {
      |  if (isEnabled(":lib-a")) devPublication(project(":lib-a"))
      |  if (isEnabled(":lib-b")) devPublication(project(":lib-b"))
      |  if (isEnabled(":")) devPublication($rootDependency)
      |}
      |""".trimMargin()

    val subproject = """
      |plugins {
      |  `java-library`
      |  `maven-publish`
      |  id("dev.adamko.dev-publish")
      |}
      |
      |group = "demo"
      |version = "1.0.0"
      |
      |publishing {
      |  publications {
      |    create<MavenPublication>("maven") {
      |      from(components["java"])
      |    }
      |  }
      |}
      |""".trimMargin()

    dir("lib-a") {
      buildGradleKts = subproject
    }
    dir("lib-b") {
      buildGradleKts = subproject
    }
  }
