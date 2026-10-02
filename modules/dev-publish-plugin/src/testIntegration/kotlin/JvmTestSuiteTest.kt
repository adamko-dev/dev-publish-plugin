package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.TaskOutcome.*

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

          shouldHaveTaskWithAnyOutcome(":test", SUCCESS, FROM_CACHE, UP_TO_DATE)

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
            |  // no `devPublish.dependency()` - the default suite must not depend on dev publishing
            |  named<JvmTestSuite>("test") {
            |    useJUnitJupiter()
            |  }
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

        createJavaFile(
          "src/main/java/FooClass.java",
          """
            |public class FooClass {
            |  public String name() { return "FooClass"; }
            |}
            |""".trimMargin()
        )

        createJavaFile(
          "src/test/java/FooClassTest.java",
          """
            |import static org.junit.jupiter.api.Assertions.assertEquals;
            |
            |import org.junit.jupiter.api.Test;
            |
            |class FooClassTest {
            |
            |  @Test
            |  void nameIsFooClass() {
            |    assertEquals("FooClass", new FooClass().name());
            |  }
            |}
            |""".trimMargin()
        )

        listOf("functionalTest", "integrationTest").forEach { suite ->
          createJavaFile(
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
