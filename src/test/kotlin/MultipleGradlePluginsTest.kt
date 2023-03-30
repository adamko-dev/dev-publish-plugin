package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.TaskOutcome

class MultipleGradlePluginsTest : FunSpec({

  test("test multiple Gradle plugins") {
    val project = project()

    project.runner
      .withArguments("clean")
      .build {
        output.shouldContain("SUCCESSFUL")
        task(":clean")?.outcome shouldBe TaskOutcome.SUCCESS
      }

    project.runner.withArguments(
      ":updateDevRepo",
      "--stacktrace",
      "--configuration-cache",
      "--build-cache",
    ).build {
      output.shouldContain("SUCCESSFUL")

      val mavenDevDir = project.projectDir.resolve("build/maven-dev")

      mavenDevDir.toTreeString { include("**/*.jar") } /*language=TEXT*/ shouldBe """
        |maven-dev/
        |└── dev/
        |    └── publish/
        |        └── plugin/
        |            └── test/
        |                └── multiple-gradle-plugins/
        |                    └── 1.2.4/
        |                        └── multiple-gradle-plugins-1.2.4.jar
      """.trimMargin()

      mavenDevDir.toTreeString { include("**/*.module") } /*language=TEXT*/ shouldBe """
        |maven-dev/
        |└── dev/
        |    └── publish/
        |        └── plugin/
        |            └── test/
        |                └── multiple-gradle-plugins/
        |                    └── 1.2.4/
        |                        └── multiple-gradle-plugins-1.2.4.module
      """.trimMargin()

      mavenDevDir.toTreeString { include("**/*.pom") } /*language=TEXT*/ shouldBe """
        |maven-dev/
        |├── dev/
        |│   └── publish/
        |│       └── plugin/
        |│           └── test/
        |│               └── multiple-gradle-plugins/
        |│                   └── 1.2.4/
        |│                       └── multiple-gradle-plugins-1.2.4.pom
        |├── plugin-alpha/
        |│   └── plugin-alpha.gradle.plugin/
        |│       └── 1.2.4/
        |│           └── plugin-alpha.gradle.plugin-1.2.4.pom
        |├── plugin-beta/
        |│   └── plugin-beta.gradle.plugin/
        |│       └── 1.2.4/
        |│           └── plugin-beta.gradle.plugin-1.2.4.pom
        |└── plugin-gamma/
        |    └── plugin-gamma.gradle.plugin/
        |        └── 1.2.4/
        |            └── plugin-gamma.gradle.plugin-1.2.4.pom
      """.trimMargin()

      mavenDevDir.toTreeString { include("**/maven-metadata.xml") } /*language=TEXT*/ shouldBe """
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
})

private fun project() = gradleKtsProjectTest("multiple-gradle-plugins") {

  buildGradleKts = """
    plugins {
      `embedded-kotlin`
      `java-gradle-plugin`
      `maven-publish`
      id("dev.adamko.dev-publish") version "+"
    }
    
    group = "dev.publish.plugin.test"
    version = "1.2.4"
    
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
      "PluginAlpha.kt", """
        import org.gradle.api.*   
        
        class PluginAlpha : Plugin<Project> {
          override fun apply(project: Project) {
            println("plugin-alpha")
          }
        }
      """.trimIndent()
    )
    createKotlinFile(
      "PluginBeta.kt", """
        import org.gradle.api.*   
        
        class PluginBeta : Plugin<Project> {
          override fun apply(project: Project) {
            println("plugin-beta")
          }
        }
      """.trimIndent()
    )
    createKotlinFile(
      "PluginGamma.kt", """
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
