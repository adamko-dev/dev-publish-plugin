package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlin.io.path.readText
import org.gradle.testkit.runner.TaskOutcome.*

/**
 * `dependencies { testImplementation(devPublish.dependency()) }` is the single declaration that makes
 * a source set depend on dev publishing.
 *
 * It is a [org.gradle.api.artifacts.ProjectDependency] on this project's own `devPublishElements`
 * variant, selected by capability, so it carries the `updateDevRepo` task dependency, puts a
 * generated metadata file on the test runtime classpath, and brings `dev-publish-utils` in
 * transitively as an ordinary module dependency.
 */
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

          // the dependency alone has to pull in the repo
          shouldHaveRunTask(":updateDevRepo")
          shouldHaveRunTask(":generateDevPublishMetadata")
        }
    }

    test("expect the recorded path is relative, so the metadata is machine-independent") {
      val metadata = project.projectDir
        .resolve("build/tmp/.maven-dev/metadata/dev/adamko/gradle/dev_publish/dev-publish.properties")
        .readText()

      withClue("an absolute path here would put the checkout location into the test task's " +
          "input fingerprint, which is what Gradle's own pluginUnderTestMetadata does") {
        // relative to the metadata directory, which is the classpath root holding this file
        metadata.trim() shouldBe "devMavenRepo=../../../maven-dev"
        metadata shouldNotContain project.projectDir.toString()
        metadata shouldNotContain ":\\"
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
            |  // the whole wiring - no manual task configuration, and no explicit
            |  // dependency on the helper library either
            |  testImplementation(devPublish.dependency())
            |
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
          """
            |import dev.adamko.gradle.dev_publish.devMavenRepo
            |import kotlin.io.path.exists
            |import kotlin.test.Test
            |import kotlin.test.assertTrue
            |
            |class DevMavenRepoDependencyTest {
            |
            |  @Test
            |  fun `devMavenRepo resolves from the classpath metadata`() {
            |    val repo = devMavenRepo()
            |
            |    val jar = repo.resolve(
            |      "foo/project/dev-maven-repo-dependency/0.0.1/dev-maven-repo-dependency-0.0.1.jar"
            |    )
            |    assertTrue(jar.exists(), "expected this project's own publication at ${'$'}jar")
            |  }
            |}
            |""".trimMargin()
        )
      }
  }
}
