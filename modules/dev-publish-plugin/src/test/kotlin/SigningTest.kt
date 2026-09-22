package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.SKIPPED

/**
 * The `signing` plugin attaches signatures to a [org.gradle.api.publish.maven.MavenPublication],
 * not to an individual repository, so signatures cannot be filtered out per-repository.
 *
 * Requiring signing credentials just to run functional tests is poor UX,
 * so DevPublish skips `Sign` tasks in builds that only publish to the dev repo.
 *
 * @see DevPublishPluginExtension.signDevPublications
 */
class SigningTest : FunSpec({

  context("when a project signs its publications, and has no signatory") {
    val project = project()

    test("expect updateDevRepo succeeds, and signing is skipped") {
      project.runner
        .withArguments(
          ":updateDevRepo",
        )
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
        .withArguments(
          ":updateDevRepo",
        )
        .forwardOutput()
        .build {
          output shouldContain "Configuration cache entry reused"
          shouldHaveTaskWithOutcome(":signMavenJavaPublication", SKIPPED)
        }
    }
  }

  context("when a build also publishes outside the dev repo") {
    val project = project()

    test("expect signing is NOT skipped, and the build fails without a signatory") {
      project.runner
        .withArguments(
          ":updateDevRepo",
          ":publishAllPublicationsToLocalRepoRepository",
        )
        .forwardOutput()
        .buildAndFail {
          output shouldContain "no configured signatory"
          shouldHaveTaskWithOutcome(":signMavenJavaPublication", FAILED)
        }
    }
  }

  context("when signDevPublications is enabled") {
    val project = project(signDevPublications = true)

    test("expect signing is required, and the build fails without a signatory") {
      project.runner
        .withArguments(
          ":updateDevRepo",
        )
        .forwardOutput()
        .buildAndFail {
          output shouldContain "no configured signatory"
          shouldHaveTaskWithOutcome(":signMavenJavaPublication", FAILED)
        }
    }
  }
}) {

  companion object {

    private fun TestScope.project(
      signDevPublications: Boolean = false,
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
            |devPublish {
            |  signDevPublications = $signDevPublications
            |}
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
