package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.intellij.lang.annotations.Language

class SingleProjectTest : FunSpec({

  context("test single-module project") {
    val project = project()

    test("project loads") {
      project.runner
        .withArguments(
          "help",
          "--stacktrace",
        )
        .build {
          output shouldContain "SUCCESSFUL"
        }
    }

    test("can depend on own publication") {
      project.runner.withArguments(
        ":updateDevRepo",
        "--stacktrace",
        "--configuration-cache",
        "--build-cache",
      ).build {
        output shouldContain "SUCCESSFUL"

        val mavenDevDir = project.projectDir.resolve("build/maven-dev")

        mavenDevDir.toTreeString() shouldBe ExpectedDevRepoTree
      }
    }
  }
}) {

  companion object {

    private fun TestScope.project(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "single-module-project",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {

        buildGradleKts = """
            plugins {
              kotlin("jvm") version embeddedKotlinVersion
              id("dev.adamko.dev-publish") version "+"
              `maven-publish`
            }
            
            group = "foo.project"
            version = "0.0.1"
            
            dependencies {
              //devPublication(project(":"))        
            }
            
            publishing {
              publications {
                create<MavenPublication>("mavenJava") {
                  from(components["java"])
                }
              }
            }
        """.trimIndent()
      }

    @Language("TEXT")
    private val ExpectedDevRepoTree = """
      maven-dev/
      └── foo/
          └── project/
              └── single-module-project/
                  ├── 0.0.1/
                  │   ├── single-module-project-0.0.1.jar
                  │   ├── single-module-project-0.0.1.jar.md5
                  │   ├── single-module-project-0.0.1.jar.sha1
                  │   ├── single-module-project-0.0.1.jar.sha256
                  │   ├── single-module-project-0.0.1.jar.sha512
                  │   ├── single-module-project-0.0.1.module
                  │   ├── single-module-project-0.0.1.module.md5
                  │   ├── single-module-project-0.0.1.module.sha1
                  │   ├── single-module-project-0.0.1.module.sha256
                  │   ├── single-module-project-0.0.1.module.sha512
                  │   ├── single-module-project-0.0.1.pom
                  │   ├── single-module-project-0.0.1.pom.md5
                  │   ├── single-module-project-0.0.1.pom.sha1
                  │   ├── single-module-project-0.0.1.pom.sha256
                  │   └── single-module-project-0.0.1.pom.sha512
                  ├── maven-metadata.xml
                  ├── maven-metadata.xml.md5
                  ├── maven-metadata.xml.sha1
                  ├── maven-metadata.xml.sha256
                  └── maven-metadata.xml.sha512
      """.trimIndent()
  }
}
