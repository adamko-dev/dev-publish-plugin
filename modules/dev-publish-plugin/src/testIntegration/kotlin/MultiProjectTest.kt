package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.intellij.lang.annotations.Language

class MultiProjectTest : FunSpec({

  context("test multi-module project") {
    val project = project()

    test("can aggregate requested projects") {
      project.runner.withArguments(
        ":project-aggregate-some:updateDevRepo",
      ).build {
        val mavenDevDir = project.projectDir.resolve("project-aggregate-some/build/maven-dev")

        mavenDevDir.toTreeString() shouldBe ExpectedDevRepoTree
      }
    }

    test("re-running after clean should also aggregate requested projects") {
      project.runner.withArguments(
        "clean",
        ":project-aggregate-some:updateDevRepo",
      ).build {
        val mavenDevDir = project.projectDir.resolve("project-aggregate-some/build/maven-dev")

        mavenDevDir.toTreeString() shouldBe ExpectedDevRepoTree
      }
    }
  }

  context("multi-module project") {
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

    test("can't aggregate all projects when some projects are missing dev-publish plugin") {
      // Gradle doesn't fully support automatic aggregation
      project.runner.withArguments(
        ":project-aggregate-all:updateDevRepo",
      ).buildAndFail {
        output shouldContain "Could not resolve project ':project-kotlin-jvm-no-dev-publish'"
      }
    }
  }
}) {

  companion object {

    private fun TestScope.project(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "multi-module project",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {

        settingsGradleKts += """
          |include(
          |  ":project-kotlin-jvm1",
          |  ":project-kotlin-jvm2",
          |
          |  ":project-kotlin-jvm-no-dev-publish", // should be filtered out during aggregation
          |
          |  ":project-aggregate-some", // explicitly depends on kotlin-jvm1, kotlin-jvm2, kotlin-jvm-no-dev-publish
          |  ":project-aggregate-all", // blindly depends on all other projects
          |)
          |""".trimMargin()

        buildGradleKts = """
          |plugins {
          |  kotlin("jvm") version embeddedKotlinVersion apply false
          |}
          |""".trimMargin()

        dir("project-kotlin-jvm1") {
          buildGradleKts = """
            |plugins {
            |  kotlin("jvm")
            |  id("dev.adamko.dev-publish") version "+"
            |  `maven-publish`
            |}
            |
            |group = "project.kotlin.jvm1"
            |version = "1.2.3"
            |
            |publishing {
            |  publications {
            |    create<MavenPublication>("mavenJava") {
            |      from(components["java"])
            |    }
            |  }
            |}
            |""".trimMargin()

          createKotlinFile(
            "src/main/kotlin/KotlinJvm1Class.kt", """
              |class KotlinJvm1Class {
              |  fun name() = "project.kotlin-jvm1"
              |}
              |""".trimMargin()
          )
        }

        dir("project-kotlin-jvm2") {
          buildGradleKts = """
            |plugins {
            |  kotlin("jvm")
            |  id("dev.adamko.dev-publish") version "+"
            |  `maven-publish`
            |}
            |
            |group = "project.kotlin.jvm2"
            |version = "9.0.1"
            |
            |publishing {
            |  publications {
            |    create<MavenPublication>("mavenJava") {
            |      from(components["java"])
            |    }
            |  }
            |}
            |""".trimMargin()

          createKotlinFile(
            "src/main/kotlin/KotlinJvm2Class.kt", """
              |class KotlinJvm2Class {
              |  fun name() = "project.kotlin.jvm2"
              |}
              |""".trimMargin()
          )
        }

        dir("project-kotlin-jvm-no-dev-publish") {
          buildGradleKts = """
            |plugins {
            |  kotlin("jvm")
            |  `maven-publish`
            |}
            |
            |group = "project.kotlin.no_dev_publish"
            |version = "4.4.0"
            |
            |publishing {
            |  publications {
            |    create<MavenPublication>("mavenJava") {
            |      from(components["java"])
            |    }
            |  }
            |}
            |""".trimMargin()

          createKotlinFile(
            "src/main/kotlin/KotlinNoDevPublishClass.kt", """
              |class KotlinNoDevPublishClass {
              |  fun name() = "project.kotlin.no_dev_publish"
              |}
              |""".trimMargin()
          )
        }

        dir("project-aggregate-some") {
          buildGradleKts = """
            |plugins {
            |  id("dev.adamko.dev-publish") version "+"
            |}
            |
            |group = "project.kotlin.aggregate.some"
            |
            |dependencies {
            |  devPublication(project(":project-kotlin-jvm1"))
            |  devPublication(project(":project-kotlin-jvm2"))
            |  // Gradle doesn't have an good way to filter invalid dependencies :(
            |  //devPublication(project(":project-kotlin-jvm-no-dev-publish"))
            |}
            |""".trimMargin()
        }

        dir("project-aggregate-all") {
          buildGradleKts = """
            |plugins {
            |  id("dev.adamko.dev-publish") version "+"
            |}
            |
            |group = "project.kotlin.aggregate.all"
            |
            |dependencies {
            |  // blindly depend on all other projects
            |  rootProject.allprojects.filter { it.path != path }.forEach {
            |    devPublication(it)
            |  }
            |}
            |""".trimMargin()
        }
      }

    @Language("TEXT")
    private val ExpectedDevRepoTree = """
      |maven-dev/
      |└── project/
      |    └── kotlin/
      |        ├── jvm1/
      |        │   └── project-kotlin-jvm1/
      |        │       ├── 1.2.3/
      |        │       │   ├── project-kotlin-jvm1-1.2.3.jar
      |        │       │   ├── project-kotlin-jvm1-1.2.3.jar.md5
      |        │       │   ├── project-kotlin-jvm1-1.2.3.jar.sha1
      |        │       │   ├── project-kotlin-jvm1-1.2.3.jar.sha256
      |        │       │   ├── project-kotlin-jvm1-1.2.3.jar.sha512
      |        │       │   ├── project-kotlin-jvm1-1.2.3.module
      |        │       │   ├── project-kotlin-jvm1-1.2.3.module.md5
      |        │       │   ├── project-kotlin-jvm1-1.2.3.module.sha1
      |        │       │   ├── project-kotlin-jvm1-1.2.3.module.sha256
      |        │       │   ├── project-kotlin-jvm1-1.2.3.module.sha512
      |        │       │   ├── project-kotlin-jvm1-1.2.3.pom
      |        │       │   ├── project-kotlin-jvm1-1.2.3.pom.md5
      |        │       │   ├── project-kotlin-jvm1-1.2.3.pom.sha1
      |        │       │   ├── project-kotlin-jvm1-1.2.3.pom.sha256
      |        │       │   └── project-kotlin-jvm1-1.2.3.pom.sha512
      |        │       ├── maven-metadata.xml
      |        │       ├── maven-metadata.xml.md5
      |        │       ├── maven-metadata.xml.sha1
      |        │       ├── maven-metadata.xml.sha256
      |        │       └── maven-metadata.xml.sha512
      |        └── jvm2/
      |            └── project-kotlin-jvm2/
      |                ├── 9.0.1/
      |                │   ├── project-kotlin-jvm2-9.0.1.jar
      |                │   ├── project-kotlin-jvm2-9.0.1.jar.md5
      |                │   ├── project-kotlin-jvm2-9.0.1.jar.sha1
      |                │   ├── project-kotlin-jvm2-9.0.1.jar.sha256
      |                │   ├── project-kotlin-jvm2-9.0.1.jar.sha512
      |                │   ├── project-kotlin-jvm2-9.0.1.module
      |                │   ├── project-kotlin-jvm2-9.0.1.module.md5
      |                │   ├── project-kotlin-jvm2-9.0.1.module.sha1
      |                │   ├── project-kotlin-jvm2-9.0.1.module.sha256
      |                │   ├── project-kotlin-jvm2-9.0.1.module.sha512
      |                │   ├── project-kotlin-jvm2-9.0.1.pom
      |                │   ├── project-kotlin-jvm2-9.0.1.pom.md5
      |                │   ├── project-kotlin-jvm2-9.0.1.pom.sha1
      |                │   ├── project-kotlin-jvm2-9.0.1.pom.sha256
      |                │   └── project-kotlin-jvm2-9.0.1.pom.sha512
      |                ├── maven-metadata.xml
      |                ├── maven-metadata.xml.md5
      |                ├── maven-metadata.xml.sha1
      |                ├── maven-metadata.xml.sha256
      |                └── maven-metadata.xml.sha512
      """.trimMargin()
  }
}
