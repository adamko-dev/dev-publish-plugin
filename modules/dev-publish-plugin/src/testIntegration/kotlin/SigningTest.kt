package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.gradle.testkit.runner.TaskOutcome.SKIPPED

/**
 * The `signing` plugin attaches signatures to a [org.gradle.api.publish.maven.MavenPublication],
 * not to an individual repository, so signatures cannot be filtered out per-repository.
 *
 * @see DevPublishPlugin.Companion.SIGNING__EXTERNAL_PUBLISHING_PROPERTY
 */
class SigningTest : FunSpec({

  context("when signing is unconditionally required, and there is no signatory") {
    val project = project(setRequired = null)

    test("expect updateDevRepo fails, and Dev Publish explains how to fix it") {
      project.runner
        .withArguments(":updateDevRepo")
        .forwardOutput()
        .buildAndFail {
          output shouldContain "Dev Publish cannot publish to the dev Maven repository if a signatory is required, but missing"
          output shouldContain "signing { setRequired(publishingOutsideDevRepo) }"
          output shouldContain "signing { setRequired { someOtherCondition() || publishingOutsideDevRepo.get() } }"
        }
    }
  }

  context("when signing is required only outside the dev repo") {
    val project = project(setRequired = "publishingOutsideDevRepo")

    test("expect updateDevRepo succeeds, and signing is skipped") {
      project.runner
        .withArguments(":updateDevRepo")
        .forwardOutput()
        .build {
          output shouldContain "SUCCESSFUL"
          shouldHaveTaskWithOutcome(":signMavenJavaPublication", SKIPPED)
        }
    }

    test("expect no signature files in the dev repo") {
      val devRepo = project.projectDir.resolve("build/maven-dev").toTreeString()
      withClue(devRepo) {
        devRepo shouldNotContain ".asc"
      }
    }

    test("expect the configuration cache is reused, and signing is still skipped") {
      project.runner
        .withArguments(":updateDevRepo")
        .forwardOutput()
        .build {
          output shouldContain "Configuration cache entry reused"
          shouldHaveTaskWithOutcome(":signMavenJavaPublication", SKIPPED)
        }
    }
  }

  context("when the same build also publishes outside the dev repo") {
    val project = project(setRequired = "publishingOutsideDevRepo")

    test("expect signing is NOT skipped, and the build fails without a signatory") {
      project.runner
        .withArguments(":updateDevRepo", ":publishAllPublicationsToLocalRepoRepository")
        .forwardOutput()
        .buildAndFail {
          output shouldContain "no configured signatory"
          // signing is genuinely required here, so Gradle's error is the correct one.
          // Dev Publish must not replace it with advice the build has already followed.
          output shouldNotContain "cannot publish to the dev Maven repository without a signatory"
        }
    }
  }

  context("when the condition is composed with another") {
    val project =
      project(setRequired = "{ publishingOutsideDevRepo.get() || false }")

    test("expect updateDevRepo succeeds, and signing is skipped") {
      project.runner
        .withArguments(":updateDevRepo")
        .forwardOutput()
        .build {
          output shouldContain "SUCCESSFUL"
          shouldHaveTaskWithOutcome(":signMavenJavaPublication", SKIPPED)
        }
    }
  }
}) {

  companion object {

    /** @param[setRequired] argument for `signing.setRequired(...)`, or `null` to leave it required. */
    private fun TestScope.project(
      setRequired: String?,
    ): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "signing-project",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {

        buildGradleKts = """
            |plugins {
            |  `java-library`
            |  `maven-publish`
            |  signing
            |  id("dev.adamko.dev-publish") version "+"
            |}
            |
            |group = "foo.project"
            |version = "0.0.1"
            |
            |publishing {
            |  repositories {
            |    maven(layout.buildDirectory.dir("local-repo")) {
            |      name = "LocalRepo"
            |    }
            |  }
            |  publications {
            |    create<MavenPublication>("mavenJava") {
            |      from(components["java"])
            |    }
            |  }
            |}
            |
            |signing {
            |  // No signatory is configured, which is the normal situation on a developer machine.
            |  sign(publishing.publications)
            |${setRequired?.let { "  setRequired($it)" } ?: ""}
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
