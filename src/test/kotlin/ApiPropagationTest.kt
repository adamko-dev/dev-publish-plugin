package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.intellij.lang.annotations.Language

class ApiPropagationTest : FunSpec({

  context("multi-module project") {
    val project = project()

    test("project loads") {
      project.runner
        .withArguments(
          "help",
          "--stacktrace",
        )
        .build {
          output.shouldContain("SUCCESSFUL")
        }
    }

    test("api dependencies are propagated") {
      project.runner.withArguments(
        ":project-aggregate:updateDevRepo",
        "--stacktrace",
        "--configuration-cache",
        "--build-cache",
      ).build {
        withClue(output) {
          output.shouldContain("SUCCESSFUL")

          val mavenDevDir = project.projectDir.resolve("project-aggregate/build/maven-dev")

          mavenDevDir.toTreeString() shouldBe ExpectedDevRepoTree
        }
      }
    }
  }
}) {
  companion object {

    private fun TestScope.project() = gradleKtsProjectTest(testCase.name.testName.replaceNonAlphaNumeric()) {

      settingsGradleKts += """
        include(
          ":project-kotlin-lib1",
          ":project-kotlin-lib2",
          ":project-kotlin-app",
        
          ":project-aggregate",
        )
      """.trimIndent()

      buildGradleKts = """
          plugins {
            kotlin("jvm") version embeddedKotlinVersion apply false
          }
      """.trimIndent()

      dir("project-kotlin-lib1") {
        buildGradleKts = """
          plugins {
            kotlin("jvm")
            id("dev.adamko.dev-publish") version "+"
            `maven-publish`
          }
          
          group = "project.kotlin.lib1"
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
          "src/main/kotlin/KotlinLib1Class.kt", """
            class KotlinLib1Class {
              fun name() = "project.kotlin.lib1"
            }
          """.trimIndent()
        )
      }

      dir("project-kotlin-lib2") {
        buildGradleKts = """
          plugins {
            kotlin("jvm")
            id("dev.adamko.dev-publish") version "+"
            `maven-publish`
          }
          
          dependencies {
            devPublicationApi(project(":project-kotlin-lib1"))        
          }
          
          group = "project.kotlin.lib2"
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
          "src/main/kotlin/KotlinLib2Class.kt", """
            class KotlinLib2Class {
              fun name() = "project.kotlin.lib2"
            }
          """.trimIndent()
        )
      }

      dir("project-kotlin-app") {
        buildGradleKts = """
          plugins {
            kotlin("jvm")
            id("dev.adamko.dev-publish") version "+"
            `maven-publish`
          }
          
          group = "project.kotlin.app"
          version = "9.9.9"
          
          dependencies {
            implementation(project(":project-kotlin-lib1"))
            implementation(project(":project-kotlin-lib2"))
          
            // don't need lib1 dependency, it's exposed as api() by lib2
            //devPublication(project(":project-kotlin-lib1")) 
            devPublicationApi(project(":project-kotlin-lib2"))  
          }
          
          publishing {
            publications {
              create<MavenPublication>("mavenJava") {
                from(components["java"])
              }
            }
          }
        """.trimIndent()

        createKotlinFile(
          "src/main/kotlin/KotlinNoDevPublishClass.kt", """
            class KotlinApp {
              fun app() = "x"
            }
          """.trimIndent()
        )
      }

      dir("project-aggregate") {
        buildGradleKts = """
          plugins {
            id("dev.adamko.dev-publish") version "+"
          }
          
          group = "project.kotlin.aggregate"
          
          dependencies {
            devPublication(project(":project-kotlin-app"))
          }
        """.trimIndent()
      }
    }

    @Language("TEXT")
    private val ExpectedDevRepoTree = """
      maven-dev/
      └── publishMavenJavaPublicationToDevPublishMavenRepository/
          └── project/
              └── kotlin/
                  ├── app/
                  │   └── project-kotlin-app/
                  │       ├── 9.9.9/
                  │       │   ├── project-kotlin-app-9.9.9.jar
                  │       │   ├── project-kotlin-app-9.9.9.jar.md5
                  │       │   ├── project-kotlin-app-9.9.9.jar.sha1
                  │       │   ├── project-kotlin-app-9.9.9.jar.sha256
                  │       │   ├── project-kotlin-app-9.9.9.jar.sha512
                  │       │   ├── project-kotlin-app-9.9.9.module
                  │       │   ├── project-kotlin-app-9.9.9.module.md5
                  │       │   ├── project-kotlin-app-9.9.9.module.sha1
                  │       │   ├── project-kotlin-app-9.9.9.module.sha256
                  │       │   ├── project-kotlin-app-9.9.9.module.sha512
                  │       │   ├── project-kotlin-app-9.9.9.pom
                  │       │   ├── project-kotlin-app-9.9.9.pom.md5
                  │       │   ├── project-kotlin-app-9.9.9.pom.sha1
                  │       │   ├── project-kotlin-app-9.9.9.pom.sha256
                  │       │   └── project-kotlin-app-9.9.9.pom.sha512
                  │       ├── maven-metadata.xml
                  │       ├── maven-metadata.xml.md5
                  │       ├── maven-metadata.xml.sha1
                  │       ├── maven-metadata.xml.sha256
                  │       └── maven-metadata.xml.sha512
                  ├── lib1/
                  │   └── project-kotlin-lib1/
                  │       ├── 1.2.3/
                  │       │   ├── project-kotlin-lib1-1.2.3.jar
                  │       │   ├── project-kotlin-lib1-1.2.3.jar.md5
                  │       │   ├── project-kotlin-lib1-1.2.3.jar.sha1
                  │       │   ├── project-kotlin-lib1-1.2.3.jar.sha256
                  │       │   ├── project-kotlin-lib1-1.2.3.jar.sha512
                  │       │   ├── project-kotlin-lib1-1.2.3.module
                  │       │   ├── project-kotlin-lib1-1.2.3.module.md5
                  │       │   ├── project-kotlin-lib1-1.2.3.module.sha1
                  │       │   ├── project-kotlin-lib1-1.2.3.module.sha256
                  │       │   ├── project-kotlin-lib1-1.2.3.module.sha512
                  │       │   ├── project-kotlin-lib1-1.2.3.pom
                  │       │   ├── project-kotlin-lib1-1.2.3.pom.md5
                  │       │   ├── project-kotlin-lib1-1.2.3.pom.sha1
                  │       │   ├── project-kotlin-lib1-1.2.3.pom.sha256
                  │       │   └── project-kotlin-lib1-1.2.3.pom.sha512
                  │       ├── maven-metadata.xml
                  │       ├── maven-metadata.xml.md5
                  │       ├── maven-metadata.xml.sha1
                  │       ├── maven-metadata.xml.sha256
                  │       └── maven-metadata.xml.sha512
                  └── lib2/
                      └── project-kotlin-lib2/
                          ├── 9.0.1/
                          │   ├── project-kotlin-lib2-9.0.1.jar
                          │   ├── project-kotlin-lib2-9.0.1.jar.md5
                          │   ├── project-kotlin-lib2-9.0.1.jar.sha1
                          │   ├── project-kotlin-lib2-9.0.1.jar.sha256
                          │   ├── project-kotlin-lib2-9.0.1.jar.sha512
                          │   ├── project-kotlin-lib2-9.0.1.module
                          │   ├── project-kotlin-lib2-9.0.1.module.md5
                          │   ├── project-kotlin-lib2-9.0.1.module.sha1
                          │   ├── project-kotlin-lib2-9.0.1.module.sha256
                          │   ├── project-kotlin-lib2-9.0.1.module.sha512
                          │   ├── project-kotlin-lib2-9.0.1.pom
                          │   ├── project-kotlin-lib2-9.0.1.pom.md5
                          │   ├── project-kotlin-lib2-9.0.1.pom.sha1
                          │   ├── project-kotlin-lib2-9.0.1.pom.sha256
                          │   └── project-kotlin-lib2-9.0.1.pom.sha512
                          ├── maven-metadata.xml
                          ├── maven-metadata.xml.md5
                          ├── maven-metadata.xml.sha1
                          ├── maven-metadata.xml.sha256
                          └── maven-metadata.xml.sha512
      """.trimIndent()
  }
}
