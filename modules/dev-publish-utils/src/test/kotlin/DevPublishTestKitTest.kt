package dev.adamko.gradle.dev_publish

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Path

class DevPublishTestKitTest : FunSpec({

  /** Creates a Maven-layout repository containing the given `group:artifact:version` coordinates. */
  fun devMavenRepo(vararg coordinates: String): Path =
    tempdir().also { repo ->
      coordinates.forEach { gav ->
        val (group, artifact, version) = gav.split(":")
        repo.resolve("${group.replace('.', '/')}/$artifact/$version")
          .apply { mkdirs() }
          .resolve("$artifact-$version.pom")
          .writeText("<project/>")
      }
    }.toPath()

  context("deriving the published groups") {

    test("expect the group of a plain library") {
      publishedGroups(devMavenRepo("com.example:lib-core:1.0.0")) shouldBe setOf("com.example")
    }

    test("expect BOTH the plugin marker group and the implementation group") {
      // This is the trap the helper exists to remove: a Gradle plugin publishes its Plugin Marker
      // Artifact under the plugin id, and its implementation under the project's group.
      val repo = devMavenRepo(
        "com.example.greeting:com.example.greeting.gradle.plugin:1.0.0",
        "com.example:greeting-plugin:1.0.0",
      )

      publishedGroups(repo) shouldBe setOf("com.example", "com.example.greeting")
    }

    test("expect groups to be sorted, so the generated script is stable") {
      val repo = devMavenRepo(
        "zeta.example:z:1.0.0",
        "alpha.example:a:1.0.0",
        "mid.example:m:1.0.0",
      )

      publishedGroups(repo).toList() shouldBe
          listOf("alpha.example", "mid.example", "zeta.example")
    }

    test("expect each group only once, however many artifacts it has") {
      val repo = devMavenRepo(
        "com.example:one:1.0.0",
        "com.example:two:1.0.0",
        "com.example:two:2.0.0",
      )

      publishedGroups(repo) shouldBe setOf("com.example")
    }

    test("expect an empty repo to have no groups") {
      publishedGroups(tempdir().toPath()).shouldBeEmpty()
    }
  }

  context("generating the repository block") {

    test("expect an includeGroup for every published group") {
      val repo = devMavenRepo(
        "com.example.greeting:com.example.greeting.gradle.plugin:1.0.0",
        "com.example:greeting-plugin:1.0.0",
      )

      devPublishRepository(repo) shouldBe """
        exclusiveContent {
          forRepository {
            maven(file("${repo.toFile().canonicalFile.invariantSeparatorsPath}")) {
              name = "DevMavenRepo"
            }
          }
          filter {
            includeGroup("com.example")
            includeGroup("com.example.greeting")
          }
        }
      """.trimIndent()
    }

    test("expect a directed error when the repo has no publications") {
      // the failure a user hits when the test task does not depend on `updateDevRepo`
      val error = shouldThrow<IllegalStateException> {
        devPublishRepository(tempdir().toPath())
      }

      error.message shouldContain "No publications found"
      error.message shouldContain "updateDevRepo"
      error.message shouldContain "implementation(devPublish.dependency())"
    }
  }

  context("generating settings blocks") {
    val repo = devMavenRepo("com.example:lib:1.0.0")

    test("expect pluginManagement to nest the repository and the fallbacks") {
      devPublishPluginManagement(repo) shouldBe """
        pluginManagement {
          repositories {
            exclusiveContent {
              forRepository {
                maven(file("${repo.toFile().canonicalFile.invariantSeparatorsPath}")) {
                  name = "DevMavenRepo"
                }
              }
              filter {
                includeGroup("com.example")
              }
            }
            gradlePluginPortal()
            mavenCentral()
          }
        }
      """.trimIndent()
    }

    test("expect fallback repositories to be optional") {
      val block = devPublishPluginManagement(repo, gradlePluginPortal = false, mavenCentral = false)

      block shouldContain "exclusiveContent {"
      block.shouldNotContainAnyOf("gradlePluginPortal()", "mavenCentral()")
    }

    test("expect dependencyResolutionManagement to wrap the same repository") {
      devPublishDependencyResolutionManagement(repo) shouldBe """
        dependencyResolutionManagement {
          repositories {
            exclusiveContent {
              forRepository {
                maven(file("${repo.toFile().canonicalFile.invariantSeparatorsPath}")) {
                  name = "DevMavenRepo"
                }
              }
              filter {
                includeGroup("com.example")
              }
            }
            mavenCentral()
          }
        }
      """.trimIndent()
    }

    test("expect a whole settings file, with the root project name last") {
      val settings = devPublishSettings(rootProjectName = "consumer", devMavenRepo = repo)

      settings shouldContain "pluginManagement {"
      settings shouldContain "dependencyResolutionManagement {"
      settings.trim() shouldEndWith """rootProject.name = "consumer""""
    }

    test("expect the root project name to be optional") {
      devPublishSettings(devMavenRepo = repo) shouldContain "pluginManagement {"
      devPublishSettings(devMavenRepo = repo).trim() shouldEndWith "}"
    }
  }
})

private fun String.shouldNotContainAnyOf(vararg substrings: String) {
  substrings.forEach { this shouldNotContain it }
}
