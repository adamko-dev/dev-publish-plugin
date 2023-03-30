package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain

class MultiProjectTest : FunSpec({

  test("test multi-module project") {
    val project = project()

    project.runner
      .withArguments("help")
      .build {
        output.shouldContain("SUCCESSFUL")
      }

    project.runner.withArguments(
      ":project-gamma:updateDevRepo",
      "--stacktrace",
      "--configuration-cache",
      "--build-cache",
    ).build {
      output.shouldContain("SUCCESSFUL")
    }
  }

})

private fun project() = gradleKtsProjectTest("multi-project-test") {

  settingsGradleKts += """
    include(
      ":project-alpha",
      ":project-beta",
      ":project-gamma",
    )
  """.trimIndent()

  dir("project-alpha") {
    buildGradleKts = """
      plugins {
        `embedded-kotlin`
        id("dev.adamko.dev-publish") version "+"
        `maven-publish`
      }
      
      version = "1.2.3"
      
      publishing {
        publications {
          create<MavenPublication>("mavenJava") {
            from(components["java"])
          }
        }
      }

    """.trimIndent()

    createKotlinFile(
      "src/main/kotlin/ProjectAlphaClass.kt", """
      class ProjectAlphaClass {
        fun name() = "project alpha"
      }
    """.trimIndent()
    )
  }

  dir("project-beta") {
    buildGradleKts = """
      plugins {
        `embedded-kotlin`
        id("dev.adamko.dev-publish") version "+"
        `maven-publish`
      }
      
      version = "9.0.1"
      
      publishing {
        publications {
          create<MavenPublication>("mavenJava") {
            from(components["java"])
          }
        }
      }

    """.trimIndent()

    createKotlinFile(
      "src/main/kotlin/ProjectBetaClass.kt", """
      class ProjectBetaClass {
        fun name() = "project beta"
      }
    """.trimIndent()
    )
  }

  dir("project-gamma") {
    buildGradleKts = """
      plugins {
        id("dev.adamko.dev-publish") version "+"
      }
      
      dependencies {
        devPublication(project(":project-alpha"))
        devPublication(project(":project-beta"))
      }
    """.trimIndent()

    createKotlinFile(
      "src/main/kotlin/ProjectBetaClass.kt", """
      class ProjectBetaClass {
        fun name() = "project beta"
      }
    """.trimIndent()
    )
  }

}
