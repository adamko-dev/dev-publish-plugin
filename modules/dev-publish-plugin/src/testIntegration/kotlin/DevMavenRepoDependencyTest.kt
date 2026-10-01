package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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
            |  kotlin("jvm") version embeddedKotlinVersion
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
  }
}
