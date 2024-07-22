package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import org.gradle.testkit.runner.TaskOutcome.*
import org.intellij.lang.annotations.Subst

class MultipleGradlePluginsTest : FunSpec({

  context("multiple Gradle plugins") {
    val project = project()

    test("clean should succeed") {
      project.runner
        .withArguments("clean")
        .build {
          output shouldContain "SUCCESSFUL"
          shouldHaveTaskWithAnyOutcome(":clean", SUCCESS, UP_TO_DATE)
        }
    }

    context("first publish") {
      project.runner
        .withArguments(
          "updateDevRepo",
//          "--info",
          "--stacktrace",
          "--configuration-cache",
          "--build-cache",
        )
        .forwardOutput()
        .build {
          test("should be successful") {
            output shouldContain "SUCCESSFUL"

            val mavenDevDir = project.projectDir.resolve("build/maven-dev")
            mavenDevDir.shouldBeMavenDevRepoWithPlugins(version = "1.2.3")

            // lifecycle task should run on the first attempt
            shouldHaveTaskWithAnyOutcome(":publishAllToDevRepo", SUCCESS)
          }

          test("should run dev-publish tasks") {
            shouldHaveTaskWithAnyOutcome(":generatePublicationHashTask", FROM_CACHE, SUCCESS)
            shouldHaveTaskWithAnyOutcome(":updateDevRepo", FROM_CACHE, SUCCESS)
          }
        }
    }

    context("second publish") {
      project.runner
        .withArguments(
          "updateDevRepo",
//          "--info",
          "--stacktrace",
          "--configuration-cache",
          "--build-cache",
        )
        .forwardOutput()
        .build {
          test("should be successful") {
            output shouldContain "SUCCESSFUL"

            val mavenDevDir = project.projectDir.resolve("build/maven-dev")
            mavenDevDir.shouldBeMavenDevRepoWithPlugins(version = "1.2.3")
          }

          test("should not re-run dev-publish tasks") {
            shouldHaveTaskWithAnyOutcome(":publishAllToDevRepo", UP_TO_DATE)
            shouldHaveTaskWithAnyOutcome(":generatePublicationHashTask", UP_TO_DATE)
            shouldHaveTaskWithAnyOutcome(":updateDevRepo", UP_TO_DATE)
          }
        }
    }

    context("when version changes") {
      project.updateVersion("4.5.6")

      test("expect old version files deleted, new version files are retained") {
        project.runner
          .withArguments(
            "updateDevRepo",
            "--stacktrace",
            "--configuration-cache",
            "--build-cache",
          )
          .forwardOutput()
          .build {
            output shouldContain "SUCCESSFUL"

            val mavenDevDir = project.projectDir.resolve("build/maven-dev")

            mavenDevDir.shouldBeMavenDevRepoWithPlugins(version = "4.5.6")
          }
      }
    }

    context("when version is SNAPSHOT") {
      project.updateVersion("8.9.0-SNAPSHOT")

      context("when one plugin changes") {
        project.dir("src/main/kotlin") {
          createKotlinFile(
            "PluginAlpha.kt", """
              import org.gradle.api.*   
              
              class PluginAlpha : Plugin<Project> {
                override fun apply(project: Project) {
                  println("plugin-alpha UPDATED to trigger recompile")
                }
              }
            """.trimIndent()
          )
        }

        test("expect old version files deleted, new version files are retained") {
          project.runner
            .withArguments(
              "updateDevRepo",
              "--stacktrace",
              "--configuration-cache",
              "--build-cache",
            )
            .forwardOutput()
            .build {
              output shouldContain "SUCCESSFUL"

              val mavenDevDir = project.projectDir.resolve("build/maven-dev")

              val snapshotVersion = mavenDevDir.toFile().walk()
                .filter { it.isFile }
                .firstNotNullOf { file ->
                  file.name
                    .substringAfter("multiple-gradle-plugins-8.9.0-", "")
                    .substringBefore(".jar", "")
                    .takeIf(String::isNotBlank)
                }

              mavenDevDir.shouldBeMavenDevRepoWithPlugins(
                version = "8.9.0-$snapshotVersion",
                directoryVersion = "8.9.0-SNAPSHOT",
              )
            }
        }
      }
    }
  }
})

private fun Path.shouldBeMavenDevRepoWithPlugins(
  @Subst("1.2.3")
  version: String,
  @Subst("1.2.3")
  directoryVersion: String = version,
  expectSnapshotMavenMetadata: Boolean = directoryVersion.endsWith("-SNAPSHOT")
) {

  toTreeString { include("**/*.jar") } shouldBe /*language=TEXT*/ """
        |maven-dev/
        |└── dev/
        |    └── publish/
        |        └── plugin/
        |            └── test/
        |                └── multiple-gradle-plugins/
        |                    └── $directoryVersion/
        |                        └── multiple-gradle-plugins-$version.jar
      """.trimMargin()

  toTreeString { include("**/*.module") } shouldBe /*language=TEXT*/ """
        |maven-dev/
        |└── dev/
        |    └── publish/
        |        └── plugin/
        |            └── test/
        |                └── multiple-gradle-plugins/
        |                    └── $directoryVersion/
        |                        └── multiple-gradle-plugins-$version.module
      """.trimMargin()

  toTreeString { include("**/*.pom") } shouldBe /*language=TEXT*/ """
        |maven-dev/
        |├── dev/
        |│   └── publish/
        |│       └── plugin/
        |│           └── test/
        |│               └── multiple-gradle-plugins/
        |│                   └── $directoryVersion/
        |│                       └── multiple-gradle-plugins-$version.pom
        |├── plugin-alpha/
        |│   └── plugin-alpha.gradle.plugin/
        |│       └── $directoryVersion/
        |│           └── plugin-alpha.gradle.plugin-$version.pom
        |├── plugin-beta/
        |│   └── plugin-beta.gradle.plugin/
        |│       └── $directoryVersion/
        |│           └── plugin-beta.gradle.plugin-$version.pom
        |└── plugin-gamma/
        |    └── plugin-gamma.gradle.plugin/
        |        └── $directoryVersion/
        |            └── plugin-gamma.gradle.plugin-$version.pom
      """.trimMargin()

  if (expectSnapshotMavenMetadata) {
    toTreeString { include("**/maven-metadata.xml") } shouldBe /*language=TEXT*/ """
        |maven-dev/
        |├── dev/
        |│   └── publish/
        |│       └── plugin/
        |│           └── test/
        |│               └── multiple-gradle-plugins/
        |│                   ├── $directoryVersion/
        |│                   │   └── maven-metadata.xml
        |│                   └── maven-metadata.xml
        |├── plugin-alpha/
        |│   └── plugin-alpha.gradle.plugin/
        |│       ├── $directoryVersion/
        |│       │   └── maven-metadata.xml
        |│       └── maven-metadata.xml
        |├── plugin-beta/
        |│   └── plugin-beta.gradle.plugin/
        |│       ├── $directoryVersion/
        |│       │   └── maven-metadata.xml
        |│       └── maven-metadata.xml
        |└── plugin-gamma/
        |    └── plugin-gamma.gradle.plugin/
        |        ├── $directoryVersion/
        |        │   └── maven-metadata.xml
        |        └── maven-metadata.xml
      """.trimMargin()
  } else {
    toTreeString { include("**/maven-metadata.xml") } shouldBe /*language=TEXT*/ """
        |maven-dev/
        |├── dev/
        |│   └── publish/
        |│       └── plugin/
        |│           └── test/
        |│               └── multiple-gradle-plugins/
        |│                   └── maven-metadata.xml
        |├── plugin-alpha/
        |│   └── plugin-alpha.gradle.plugin/
        |│       └── maven-metadata.xml
        |├── plugin-beta/
        |│   └── plugin-beta.gradle.plugin/
        |│       └── maven-metadata.xml
        |└── plugin-gamma/
        |    └── plugin-gamma.gradle.plugin/
        |        └── maven-metadata.xml
      """.trimMargin()
  }
}

private fun TestScope.project(): GradleProjectTest =
  gradleKtsProjectTest(
    projectName = "multiple-gradle-plugins",
    testProjectPath = testCase.descriptor.slashSeparatedPath(),
  ) {

    buildGradleKts = """
      plugins {
        `embedded-kotlin`
        `java-gradle-plugin`
        `maven-publish`
        id("dev.adamko.dev-publish") version "+"
      }
      
      group = "dev.publish.plugin.test"
      version = "1.2.3"
      
      gradlePlugin {
        isAutomatedPublishing = true
      
        plugins.register("alpha") {
          id = "plugin-alpha"
          displayName = "PluginAlpha"
          implementationClass = "PluginAlpha"
        }
        plugins.register("beta") {
          id = "plugin-beta"
          displayName = "PluginBeta"
          implementationClass = "PluginBeta"
        }
        plugins.register("gamma") {
          id = "plugin-gamma"
          displayName = "PluginGamma"
          implementationClass = "PluginGamma"
        }
      }
    """.trimIndent()

    dir("src/main/kotlin") {
      createKotlinFile(
        "PluginAlpha.kt",
        """
          import org.gradle.api.*   
          
          class PluginAlpha : Plugin<Project> {
            override fun apply(project: Project) {
              println("plugin-alpha")
            }
          }
        """.trimIndent()
      )
      createKotlinFile(
        "PluginBeta.kt",
        """
          import org.gradle.api.*   
          
          class PluginBeta : Plugin<Project> {
            override fun apply(project: Project) {
              println("plugin-beta")
            }
          }
        """.trimIndent()
      )
      createKotlinFile(
        "PluginGamma.kt",
        """
          import org.gradle.api.*   
          
          class PluginGamma : Plugin<Project> {
            override fun apply(project: Project) {
              println("plugin-gamma")
            }
          }
        """.trimIndent()
      )
    }
  }


private fun GradleProjectTest.updateVersion(version: String) {
  buildGradleKts = buildGradleKts
    .lines()
    .joinToString("\n") { line ->
      if (line.startsWith("""version = """)) {
        """version = "$version""""
      } else {
        line
      }
    }
}
