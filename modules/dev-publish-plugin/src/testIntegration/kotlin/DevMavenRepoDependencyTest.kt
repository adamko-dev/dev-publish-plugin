package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.*
import kotlin.io.path.deleteRecursively
import kotlin.io.path.readText
import org.gradle.testkit.runner.TaskOutcome.*

class DevMavenRepoDependencyTest : FunSpec({

  context("a project that only declares devPublish.dependency") {
    val project = project()

    test("expect the tests run, and updateDevRepo runs for them") {
      project.runner
        .withArguments(
          ":test",
        )
        .forwardOutput()
        .build {
          output shouldContain "SUCCESSFUL"

          shouldHaveTaskWithAnyOutcome(":test", SUCCESS, FROM_CACHE, UP_TO_DATE)

          shouldHaveRunTask(":updateDevRepo")
          shouldHaveRunTask(":generateDevPublishMetadata")
        }
    }

    test("expect the path is relative") {
      project.runner.withArguments(":generateDevPublishMetadata").forwardOutput().build {
        val repoLocation = project.projectDir
          .resolve("build/tmp/.maven-dev/metadata/dev.publish.metadata.repo-location.txt")
          .readText()
          .trim()

        assertSoftly {
          repoLocation shouldBe "../../../maven-dev"
          repoLocation shouldNotContain project.projectDir.toString()
          repoLocation shouldNotContain ":\\"
        }
      }
    }

    test("expect a checksum of the repo contents") {
      project.runner.withArguments(":generateDevPublishMetadata").forwardOutput().build {
        val repoChecksum = project.projectDir
          .resolve("build/tmp/.maven-dev/metadata/dev.publish.metadata.repo-checksum.txt")
          .readText()
          .trim()

        assertSoftly {
          repoChecksum shouldNotBe "no-files"
          repoChecksum.shouldBeSingleLine()
          repoChecksum shouldHaveLength 64 // SHA-256 checksum length
          repoChecksum.shouldBeHexadecimal()
        }
      }
    }
  }

  context("a project where updateDevRepo is disabled") {
    // the metadata file still records where the repo would be, so the recorded path resolves to a
    // directory that was never created
    val project = projectWithDisabledUpdateDevRepo()

    test("expect devMavenRepo() reports the missing repo, rather than an empty one") {
      project.runner.withArguments(":test").buildAndFail {
        shouldHaveTaskWithOutcome(":updateDevRepo", SKIPPED)

        output shouldContain "The dev Maven repository could not be located."
        output shouldContain "was recorded as '../../../maven-dev'"
        output shouldContain "but nothing exists at"
        output shouldContain "Run the `updateDevRepo` task to create it."
      }
    }
  }

  context("a project that uses dev-publish-utils without devPublish.dependency") {
    // `dev-publish-utils` can read the metadata file, but only `devPublish.dependency()` puts it on
    // the classpath, so this is the mistake a user is most likely to make
    val project = projectWithoutDependency()

    test("expect devMavenRepo() explains that the metadata is missing, and how to add it") {
      project.runner.withArguments(":test").buildAndFail {
        output shouldContain "The dev Maven repository could not be located."
        output shouldContain "could not find /dev.publish.metadata.repo-location.txt"
        output shouldContain "dependencies { implementation(devPublish.dependency()) }"
      }
    }
  }
}) {

  companion object {

    private fun TestScope.project(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "dev-maven-repo-dependency",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {

        buildGradleKts = """
            |plugins {
            |  kotlin("jvm") version "2.2.21"
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
            |  testImplementation(kotlin("test"))
            |}
            |
            |tasks.test {
            |  useJUnitPlatform()
            |}
            |""".trimMargin()

        createKotlinFile(
          "src/main/kotlin/FooClass.kt",
          """
            |class FooClass {
            |  fun name(): String = "FooClass"
            |}
            |""".trimMargin()
        )

        createKotlinFile(
          "src/test/kotlin/DevMavenRepoDependencyTest.kt",
          $$"""
            |import dev.adamko.gradle.dev_publish.devMavenRepo
            |import kotlin.io.path.exists
            |import kotlin.test.Test
            |import kotlin.test.assertTrue
            |
            |class DevMavenRepoDependencyTest {
            |
            |  @Test
            |  fun canAccessDevMavenRepo() {
            |    val repo = devMavenRepo()
            |    val jar = repo.resolve("foo/project/dev-maven-repo-dependency/0.0.1/dev-maven-repo-dependency-0.0.1.jar")
            |    assertTrue(jar.exists(), "expected this project's own publication at $jar")
            |  }
            |}
            |""".trimMargin()
        )
      }

    /**
     * `updateDevRepo` never runs, so the dev Maven repository is never created - but
     * `generateDevPublishMetadata` still records where it would have been.
     */
    private fun TestScope.projectWithDisabledUpdateDevRepo(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "dev-maven-repo-disabled",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {
        // an earlier run would have left a dev repo behind, and then it would exist after all
        file("build").deleteRecursively()

        buildGradleKts = """
            |plugins {
            |  kotlin("jvm") version "2.2.21"
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
            |tasks.named("updateDevRepo") {
            |  onlyIf { false }
            |}
            |
            |dependencies {
            |  testImplementation(devPublish.dependency())
            |  testImplementation(kotlin("test"))
            |}
            |
            |tasks.test {
            |  useJUnitPlatform()
            |  // otherwise the exception message only reaches the HTML report, not the build output
            |  testLogging {
            |    showExceptions = true
            |    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            |  }
            |}
            |""".trimMargin()

        createKotlinFile(
          "src/test/kotlin/DisabledUpdateDevRepoTest.kt",
          """
            |import dev.adamko.gradle.dev_publish.devMavenRepo
            |import kotlin.test.Test
            |
            |class DisabledUpdateDevRepoTest {
            |
            |  @Test
            |  fun `devMavenRepo fails when the repo was never created`() {
            |    devMavenRepo()
            |  }
            |}
            |""".trimMargin()
        )
      }

    /**
     * Depends on `dev-publish-utils` directly, and never on `devPublish.dependency()`, so
     * `devMavenRepo()` is callable but the metadata file it reads is not on the classpath.
     */
    private fun TestScope.projectWithoutDependency(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "dev-maven-repo-no-dependency",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {

        buildGradleKts = """
            |plugins {
            |  kotlin("jvm") version "2.2.21"
            |}
            |
            |dependencies {
            |  testImplementation("dev.adamko.gradle:dev-publish-utils:+")
            |  testImplementation(kotlin("test"))
            |}
            |
            |tasks.test {
            |  useJUnitPlatform()
            |  testLogging {
            |    showExceptions = true
            |    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            |  }
            |}
            |""".trimMargin()

        createKotlinFile(
          "src/test/kotlin/NoMetadataTest.kt",
          """
            |import dev.adamko.gradle.dev_publish.devMavenRepo
            |import kotlin.test.Test
            |
            |class NoMetadataTest {
            |
            |  @Test
            |  fun `devMavenRepo fails without the metadata file`() {
            |    devMavenRepo()
            |  }
            |}
            |""".trimMargin()
        )
      }

    private fun CharSequence.shouldBeHexadecimal() {
      this shouldMatch Regex("[0-9a-fA-F]+")
    }
  }
}
