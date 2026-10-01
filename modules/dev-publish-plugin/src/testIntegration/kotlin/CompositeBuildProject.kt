package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import dev.adamko.gradle.dev_publish.test_utils.GradleProjectTest.Companion.settingRepositories
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language

class CompositeBuildProject : FunSpec({

  context("test composite build project") {
    val project = project()

    test("project clean") {
      project.runner
        .withArguments("clean")
        .build()
    }

    context("when lib updates dev repo") {
      project.runner
        .forwardOutput()
        .withArguments(
          ":lib:updateDevRepo",
          "--info",
        ).build {

          test("expect dev repo contains expected files") {
            val mavenDevDir = project.projectDir.resolve("lib/build/maven-dev")
              .toTreeString()
              .replace(Regex("""-\d{8}\.\d{6}-"""), "-{timestamp}-")

            mavenDevDir shouldBe ExpectedDevRepoTree
          }

          test("expect included build generates checksums") {
            val dataModelChecksums =
              project.projectDir.resolve("data-model/build/tmp/.maven-dev/checksum-store").toTreeString()

            dataModelChecksums shouldBe """
              |checksum-store/
              |└── mavenJava.txt
              """.trimMargin()
          }
        }

      context("lib updates dev repo after clean") {
        test("project clean") {
          project.runner
            .withArguments(
              "clean",
              "--info",
            )
            .build()
        }

        project.runner
          .forwardOutput()
          .withArguments(
            ":lib:updateDevRepo",
            "--info",
          ).build {
            test("expect dev repo contains expected files") {
              val mavenDevDir = project.projectDir.resolve("lib/build/maven-dev")
                .toTreeString()
                .replace(Regex("""-\d{8}\.\d{6}-"""), "-{timestamp}-")

              mavenDevDir shouldBe ExpectedDevRepoTree
            }

            test("expect included build generates checksums") {
              val dataModelChecksums =
                project.projectDir.resolve("data-model/build/tmp/.maven-dev/checksum-store").toTreeString()

              dataModelChecksums shouldBe """
                |checksum-store/
                |└── mavenJava.txt
                """.trimMargin()
            }
          }
      }
    }
  }
}) {

  companion object {

    private fun TestScope.project(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "compose-build-project",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {

        settingsGradleKts += """
          |
          |includeBuild("data-model")
          |
          |include(":lib")
          |""".trimMargin()

        buildGradleKts = """
          |plugins {
          |  base
          |  kotlin("jvm") version embeddedKotlinVersion apply false
          |  id("dev.adamko.dev-publish") version "+" apply false
          |}
          |
          |tasks.clean {
          |  dependsOn(gradle.includedBuild("data-model").task(":clean"))  
          |}
          |""".trimMargin()

        dir("data-model") {

          settingsGradleKts = """
            |rootProject.name = "data-model"
            |$settingRepositories
            |""".trimMargin()

          buildGradleKts = """
            |plugins {
            |  kotlin("jvm") version embeddedKotlinVersion
            |  id("dev.adamko.dev-publish") version "+"
            |  `maven-publish`
            |}
            |
            |group = "demo"
            |version = "main-SNAPSHOT"
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
            "src/main/kotlin/FooData.kt", """
              |data class FooData(
              |  val name: String
              |)
              |""".trimMargin()
          )
        }

        dir("lib") {
          buildGradleKts = """
            |plugins {
            |  kotlin("jvm")
            |  id("dev.adamko.dev-publish")
            |  `maven-publish`
            |}
            |
            |group = "demo"
            |version = "main-SNAPSHOT"
            |
            |dependencies {
            |  implementation("demo:data-model")
            |  devPublication("demo:data-model")
            |}
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
            "src/main/kotlin/Lib.kt", """
              |class LibCls {
              |  fun name() = "LibCls"
              |  val data = FooData("lib")
              |}
              |""".trimMargin()
          )
        }
      }

    @Language("TEXT")
    private val ExpectedDevRepoTree = """
          |maven-dev/
          |└── demo/
          |    ├── data-model/
          |    │   ├── main-SNAPSHOT/
          |    │   │   ├── data-model-main-{timestamp}-1.jar
          |    │   │   ├── data-model-main-{timestamp}-1.jar.md5
          |    │   │   ├── data-model-main-{timestamp}-1.jar.sha1
          |    │   │   ├── data-model-main-{timestamp}-1.jar.sha256
          |    │   │   ├── data-model-main-{timestamp}-1.jar.sha512
          |    │   │   ├── data-model-main-{timestamp}-1.module
          |    │   │   ├── data-model-main-{timestamp}-1.module.md5
          |    │   │   ├── data-model-main-{timestamp}-1.module.sha1
          |    │   │   ├── data-model-main-{timestamp}-1.module.sha256
          |    │   │   ├── data-model-main-{timestamp}-1.module.sha512
          |    │   │   ├── data-model-main-{timestamp}-1.pom
          |    │   │   ├── data-model-main-{timestamp}-1.pom.md5
          |    │   │   ├── data-model-main-{timestamp}-1.pom.sha1
          |    │   │   ├── data-model-main-{timestamp}-1.pom.sha256
          |    │   │   ├── data-model-main-{timestamp}-1.pom.sha512
          |    │   │   ├── maven-metadata.xml
          |    │   │   ├── maven-metadata.xml.md5
          |    │   │   ├── maven-metadata.xml.sha1
          |    │   │   ├── maven-metadata.xml.sha256
          |    │   │   └── maven-metadata.xml.sha512
          |    │   ├── maven-metadata.xml
          |    │   ├── maven-metadata.xml.md5
          |    │   ├── maven-metadata.xml.sha1
          |    │   ├── maven-metadata.xml.sha256
          |    │   └── maven-metadata.xml.sha512
          |    └── lib/
          |        ├── main-SNAPSHOT/
          |        │   ├── lib-main-{timestamp}-1.jar
          |        │   ├── lib-main-{timestamp}-1.jar.md5
          |        │   ├── lib-main-{timestamp}-1.jar.sha1
          |        │   ├── lib-main-{timestamp}-1.jar.sha256
          |        │   ├── lib-main-{timestamp}-1.jar.sha512
          |        │   ├── lib-main-{timestamp}-1.module
          |        │   ├── lib-main-{timestamp}-1.module.md5
          |        │   ├── lib-main-{timestamp}-1.module.sha1
          |        │   ├── lib-main-{timestamp}-1.module.sha256
          |        │   ├── lib-main-{timestamp}-1.module.sha512
          |        │   ├── lib-main-{timestamp}-1.pom
          |        │   ├── lib-main-{timestamp}-1.pom.md5
          |        │   ├── lib-main-{timestamp}-1.pom.sha1
          |        │   ├── lib-main-{timestamp}-1.pom.sha256
          |        │   ├── lib-main-{timestamp}-1.pom.sha512
          |        │   ├── maven-metadata.xml
          |        │   ├── maven-metadata.xml.md5
          |        │   ├── maven-metadata.xml.sha1
          |        │   ├── maven-metadata.xml.sha256
          |        │   └── maven-metadata.xml.sha512
          |        ├── maven-metadata.xml
          |        ├── maven-metadata.xml.md5
          |        ├── maven-metadata.xml.sha1
          |        ├── maven-metadata.xml.sha256
          |        └── maven-metadata.xml.sha512
          """.trimMargin()
  }
}
