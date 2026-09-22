package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.TaskOutcome.*

/**
 * `devPublish.dependency()` is documented as working with the `jvm-test-suite` plugin, where the
 * `Test` task is named after the suite rather than being the default `test` task.
 *
 * The suites here declare it through both routes - the suite's own `dependencies { }` block, and the
 * suite's generated configuration - and their tests locate the repo through [devMavenRepo], so this
 * fails if the dependency does not reach the suite's own test JVM.
 */
class JvmTestSuiteTest : FunSpec({

  context("a project with two jvm-test-suite suites") {
    val project = project()

    test("expect both suites run, and updateDevRepo runs for them") {
      project.runner
        .withArguments(
          ":functionalTest",
          ":integrationTest",
        )
        .forwardOutput()
        .build {
          output shouldContain "SUCCESSFUL"

          shouldHaveTaskWithAnyOutcome(":functionalTest", SUCCESS, FROM_CACHE, UP_TO_DATE)
          shouldHaveTaskWithAnyOutcome(":integrationTest", SUCCESS, FROM_CACHE, UP_TO_DATE)

          // the dependency edge has to be attached to the suite tasks, not only to `test`
          shouldHaveRunTask(":updateDevRepo")

          // the wiring is per-suite - the default `test` task was never asked for
          shouldNotHaveRunTask(":test")
        }
    }

    test("expect the default test task does not trigger updateDevRepo") {
      project.runner
        .withArguments(
          ":test",
        )
        .forwardOutput()
        .build {
          output shouldContain "SUCCESSFUL"

          shouldHaveRunTask(":test")

          // `test` declares no dependency, so it gets no dev repo - the wiring is not global
          shouldNotHaveRunTask(":updateDevRepo")
          shouldNotHaveRunTask(":functionalTest")
          shouldNotHaveRunTask(":integrationTest")
        }
    }
  }
}) {

  companion object {

    private fun TestScope.project(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "jvm-test-suite-project",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {

        buildGradleKts = """
            |plugins {
            |  `java-library`
            |  `jvm-test-suite`
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
            |testing.suites {
            |  register<JvmTestSuite>("functionalTest") {
            |    useJUnitJupiter()
            |    dependencies {
            |      implementation(devPublish.dependency())
            |    }
            |  }
            |  register<JvmTestSuite>("integrationTest") {
            |    useJUnitJupiter()
            |    dependencies {
            |      implementation(devPublish.dependency())
            |    }
            |  }
            |}
            |""".trimMargin()

        createFile(
          "src/main/java/FooClass.java",
          """
            |public class FooClass {
            |  public String name() { return "FooClass"; }
            |}
            |""".trimMargin()
        )

        listOf("functionalTest", "integrationTest").forEach { suite ->
          createFile(
            "src/$suite/java/DevMavenRepoTest.java",
            """
              |import static org.junit.jupiter.api.Assertions.assertTrue;
              |
              |import dev.adamko.gradle.dev_publish.DevPublishTestKit;
              |import java.nio.file.Files;
              |import java.nio.file.Path;
              |import org.junit.jupiter.api.Test;
              |
              |class DevMavenRepoTest {
              |
              |  @Test
              |  void devMavenRepoIsAvailableToThisSuite() {
              |    // fails if the metadata never reached the $suite runtime classpath
              |    Path repo = DevPublishTestKit.devMavenRepo();
              |
              |    Path jar = repo.resolve("foo/project/jvm-test-suite-project/0.0.1/jvm-test-suite-project-0.0.1.jar");
              |    assertTrue(Files.exists(jar), "expected this project's own publication at " + jar);
              |  }
              |}
              |""".trimMargin()
          )
        }
      }
  }
}
