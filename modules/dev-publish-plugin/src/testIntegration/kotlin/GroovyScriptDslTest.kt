package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.GradleProjectTest
import dev.adamko.gradle.dev_publish.test_utils.GradleProjectTest.Companion.settingRepositories
import dev.adamko.gradle.dev_publish.test_utils.build
import dev.adamko.gradle.dev_publish.test_utils.createFile
import dev.adamko.gradle.dev_publish.test_utils.slashSeparatedPath
import dev.adamko.gradle.dev_publish.test_utils.toTreeString
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.intellij.lang.annotations.Language

class GroovyScriptDslTest : FunSpec({

  context("a project with Groovy DSL scripts") {
    val project = project()

    test("expect the generated Groovy settings resolve the plugin, and dev publishing works") {
      project.runner.withArguments(":updateDevRepo").build {
        output shouldContain "SUCCESSFUL"

        val mavenDevDir = project.projectDir.resolve("build/maven-dev")

        mavenDevDir.toTreeString() shouldBe ExpectedDevRepoTree
      }
    }
  }
}) {

  companion object {

    private fun TestScope.project(): GradleProjectTest =
      GradleProjectTest(
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
        projectName = "groovy-dsl-project",
      ).apply {

        val settingsRepositoriesGroovy =
          settingRepositories.replace(Regex("""maven\(file\("([^"]+)"\)\) \{"""), "maven {\n  url = '$1'")

        createFile(
          "settings.gradle",
          """
            |${settingsRepositoriesGroovy}
            |
            |rootProject.name = '$projectName'
            |""".trimMargin()
        )

        createFile(
          "build.gradle",
          """
          |plugins {
          |  id 'java'
          |  id 'maven-publish'
          |  id 'dev.adamko.dev-publish' version '+'
          |}
          |
          |group = 'groovy.dsl'
          |version = '1.0.0'
          |
          |publishing {
          |  publications {
          |    mavenJava(MavenPublication) {
          |      from components.java
          |    }
          |  }
          |}
          |""".trimMargin()
        )
      }

    @Language("TEXT")
    private val ExpectedDevRepoTree = """
      maven-dev/
      └── groovy/
          └── dsl/
              └── groovy-dsl-project/
                  ├── 1.0.0/
                  │   ├── groovy-dsl-project-1.0.0.jar
                  │   ├── groovy-dsl-project-1.0.0.jar.md5
                  │   ├── groovy-dsl-project-1.0.0.jar.sha1
                  │   ├── groovy-dsl-project-1.0.0.jar.sha256
                  │   ├── groovy-dsl-project-1.0.0.jar.sha512
                  │   ├── groovy-dsl-project-1.0.0.module
                  │   ├── groovy-dsl-project-1.0.0.module.md5
                  │   ├── groovy-dsl-project-1.0.0.module.sha1
                  │   ├── groovy-dsl-project-1.0.0.module.sha256
                  │   ├── groovy-dsl-project-1.0.0.module.sha512
                  │   ├── groovy-dsl-project-1.0.0.pom
                  │   ├── groovy-dsl-project-1.0.0.pom.md5
                  │   ├── groovy-dsl-project-1.0.0.pom.sha1
                  │   ├── groovy-dsl-project-1.0.0.pom.sha256
                  │   └── groovy-dsl-project-1.0.0.pom.sha512
                  ├── maven-metadata.xml
                  ├── maven-metadata.xml.md5
                  ├── maven-metadata.xml.sha1
                  ├── maven-metadata.xml.sha256
                  └── maven-metadata.xml.sha512
      """.trimIndent()
  }
}
