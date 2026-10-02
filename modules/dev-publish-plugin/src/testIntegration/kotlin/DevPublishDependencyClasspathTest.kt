package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * Test that `devPublication` dependencies do leak onto the classpath of a source set that declares
 * [DevPublishPluginExtension.dependency].
 */
class DevPublishDependencyClasspathTest : FunSpec({

  context("a project that devPublications another project, and depends on dev publishing") {
    val project = project()

    test("expect the devPublication is not on the test compile classpath") {
      project.runner.withArguments(
        ":tests:dependencies",
        "--configuration", "testCompileClasspath",
      ).build {
        output shouldNotContain "project ':lib'"
        // the dev repo metadata, and the library that reads it, are still there
        output shouldContain "dev-publish-utils"
      }
    }

    test("expect the devPublication is not on the test runtime classpath") {
      project.runner.withArguments(
        ":tests:dependencies",
        "--configuration", "testRuntimeClasspath",
      ).build {
        output shouldNotContain "project ':lib'"
        output shouldContain "dev-publish-utils"
      }
    }

    test("expect test code cannot compile against the devPublication") {
      project.runner.withArguments(
        ":tests:compileTestKotlin",
      ).buildAndFail {
        output shouldContain "LibClassLeakTest.kt"
        output shouldContain "Unresolved reference 'example'"
      }
    }
  }
}) {
  companion object {

    private fun TestScope.project() =
      gradleKtsProjectTest(
        projectName = "dev publish dependency classpath",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {

        settingsGradleKts += """
          |include(
          |  ":lib",
          |  ":tests",
          |)
          |""".trimMargin()

        buildGradleKts = """
          |plugins {
          |  kotlin("jvm") version embeddedKotlinVersion apply false
          |}
          |""".trimMargin()

        dir("lib") {
          buildGradleKts = """ 
            |plugins {
            |  kotlin("jvm")
            |  `maven-publish`
            |  id("dev.adamko.dev-publish") version "+"
            |}
            |
            |group = "com.example"
            |version = "1.2.3"
            |
            |publishing {
            |  publications {
            |    create<MavenPublication>("mavenJava") {
            |      from(components["java"])
            |    }
            |  }
            |}
            """.trimMargin()

          createKotlinFile(
            "src/main/kotlin/LibClass.kt",
            """
              |package com.example.lib
              |
              |class LibClass {
              |  fun name() = "com.example.lib"
              |}
              |""".trimMargin()
          )
        }

        dir("tests") {
          buildGradleKts = """
            |plugins {
            |  kotlin("jvm")
            |  id("dev.adamko.dev-publish") version "+"
            |}
            |
            |dependencies {
            |  devPublication(project(":lib"))
            |
            |  testImplementation(devPublish.dependency())
            |}
            |""".trimMargin()

          createKotlinFile(
            "src/test/kotlin/LibClassLeakTest.kt",
            """
              |// Must not compile: :lib is only reachable through the dev Maven repo.
              |import com.example.lib.LibClass
              |
              |class LibClassLeakTest {
              |  fun leaked() = LibClass().name()
              |}
              |""".trimMargin()
          )
        }
      }
  }
}
