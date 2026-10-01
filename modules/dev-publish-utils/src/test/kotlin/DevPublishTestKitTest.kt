package dev.adamko.gradle.dev_publish

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

class DevPublishTestKitTest : FunSpec({

  /** Creates a Maven-layout repository containing the given `group:artifact:version` coordinates. */
  fun devMavenRepo(vararg coordinates: String, dir: File = tempdir()): Path =
    dir.also { repo ->
      coordinates.forEach { gav ->
        val (group, artifact, version) = gav.split(":")
        repo.resolve("${group.replace('.', '/')}/$artifact/$version")
          .apply { mkdirs() }
          .resolve("$artifact-$version.pom")
          .writeText("<project/>")
      }
    }.toPath()

  /**
   * The groups the generated `exclusiveContent` block includes, in the order it writes them.
   *
   * Read back from the generated code, because deriving the groups is an implementation detail -
   * what callers depend on is the filter that ends up in their settings script.
   */
  fun includedGroups(devMavenRepo: Path): List<String> =
    devPublishRepository(devMavenRepo)
      .lines()
      .map { it.trim() }
      .filter { it.startsWith("includeGroup(") }
      .map { it.substringAfter("includeGroup(\"").substringBefore("\")") }

  context("deriving the published groups") {

    test("expect the group of a plain library") {
      includedGroups(devMavenRepo("com.example:lib-core:1.0.0")) shouldBe listOf("com.example")
    }

    test("expect BOTH the plugin marker group and the implementation group") {
      val repo = devMavenRepo(
        "com.example.greeting:com.example.greeting.gradle.plugin:1.0.0",
        "com.example:greeting-plugin:1.0.0",
      )

      includedGroups(repo) shouldBe listOf("com.example", "com.example.greeting")
    }

    test("expect groups to be sorted, so the generated script is stable") {
      val repo = devMavenRepo(
        "zeta.example:z:1.0.0",
        "alpha.example:a:1.0.0",
        "mid.example:m:1.0.0",
      )

      includedGroups(repo) shouldBe listOf("alpha.example", "mid.example", "zeta.example")
    }

    test("expect each group only once, however many artifacts it has") {
      val repo = devMavenRepo(
        "com.example:one:1.0.0",
        "com.example:two:1.0.0",
        "com.example:two:2.0.0",
      )

      includedGroups(repo) shouldBe listOf("com.example")
    }

    // an empty repo produces no block at all - covered by "a directed error when the repo has no
    // publications" in the next context
  }

  context("generating the repository block") {

    test("expect an includeGroup for every published group") {
      val repo = devMavenRepo(
        "com.example.greeting:com.example.greeting.gradle.plugin:1.0.0",
        "com.example:greeting-plugin:1.0.0",
      )

      devPublishRepository(repo) shouldBe /*language=kotlin*/ """
        |exclusiveContent {
        |  forRepository {
        |    maven(file("${repo.invariantSeparatorsPathString}")) {
        |      name = "DevMavenRepo"
        |    }
        |  }
        |  filter {
        |    includeGroup("com.example")
        |    includeGroup("com.example.greeting")
        |  }
        |}
        |""".trimMargin()
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
      devPublishPluginManagement(repo) shouldBe /*language=kotlin*/ """
        |pluginManagement {
        |  repositories {
        |    exclusiveContent {
        |      forRepository {
        |        maven(file("${repo.invariantSeparatorsPathString}")) {
        |          name = "DevMavenRepo"
        |        }
        |      }
        |      filter {
        |        includeGroup("com.example")
        |      }
        |    }
        |    gradlePluginPortal()
        |    mavenCentral()
        |  }
        |}
        |""".trimMargin()
    }

    test("expect fallback repositories to be optional") {
      val block = devPublishPluginManagement(repo, gradlePluginPortal = false, mavenCentral = false)

      block shouldContain "exclusiveContent {"
      block shouldNotContain "gradlePluginPortal()"
      block shouldNotContain "mavenCentral()"
    }

    test("expect dependencyResolutionManagement to wrap the same repository") {
      devPublishDependencyResolutionManagement(repo) shouldBe /*language=kotlin*/ """
        |dependencyResolutionManagement {
        |  repositories {
        |    exclusiveContent {
        |      forRepository {
        |        maven(file("${repo.invariantSeparatorsPathString}")) {
        |          name = "DevMavenRepo"
        |        }
        |      }
        |      filter {
        |        includeGroup("com.example")
        |      }
        |    }
        |    mavenCentral()
        |  }
        |}
        |""".trimMargin()
    }

    test("expect a whole settings file, with the root project name last") {
      val settings = devPublishSettings(rootProjectName = "consumer", devMavenRepo = repo)

      settings shouldContain "pluginManagement {"
      settings shouldContain "dependencyResolutionManagement {"
      settings.trim() shouldEndWith """rootProject.name = "consumer""""
    }

    test("expect the root project name to be optional") {
      devPublishSettings(rootProjectName = null, devMavenRepo = repo) shouldNotContain "rootProject.name"
    }
  }

  context("generating Groovy scripts") {
    val repo = devMavenRepo("com.example:lib:1.0.0")

    test("expect a url assignment, because Groovy has no maven(url) { } overload") {
      devPublishRepository(repo, ScriptDsl.Groovy) shouldBe /*language=groovy*/ """
        |exclusiveContent {
        |  forRepository {
        |    maven {
        |      url = file('${repo.invariantSeparatorsPathString}')
        |      name = 'DevMavenRepo'
        |    }
        |  }
        |  filter {
        |    includeGroup('com.example')
        |  }
        |}
        |""".trimMargin()
    }

    test("expect the settings blocks to nest the Groovy repository") {
      val settings = devPublishSettings("consumer", repo, ScriptDsl.Groovy)

      settings shouldContain "pluginManagement {"
      settings shouldContain "url = file('"
      settings shouldNotContain "maven(file("
      settings.trim() shouldEndWith "rootProject.name = 'consumer'"
    }
  }

  // TODO are these test worthwhile keeping, or is DevMavenRepoDependencyTest enough?
//  context("locating the repo from the classpath metadata") {
//
//    /** Lays out a classpath root holding the metadata file, and returns its URL. */
//    fun metadataUrl(dir: File, recordedPath: String): URL {
//      val metadata = dir.toPath().resolve(DevPublishMetadata.METADATA_FILE_PATH)
//      DevPublishMetadata.write(metadata, recordedPath, devMavenRepoChecksum = "abc123")
//      return URLClassLoader(arrayOf(dir.toURI().toURL()))
//        .getResource(DevPublishMetadata.METADATA_FILE_PATH)!!
//    }
//
//    test("expect the recorded path resolves against the classpath root, not the metadata file") {
//      val dir = tempdir()
//      val repo = dir.resolve("maven-dev").apply { mkdirs() }
//
//      devMavenRepoFrom(metadataUrl(dir, "maven-dev")) shouldBe repo.toPath()
//    }
//
//    test("expect a path that climbs out of the classpath root resolves too") {
//      val dir = tempdir()
//      val repo = dir.parentFile.resolve("maven-dev").apply { mkdirs() }
//
//      devMavenRepoFrom(metadataUrl(dir, "../maven-dev")) shouldBe repo.toPath()
//    }
//
//    test("expect a repo that does not exist is reported, rather than returned") {
//      val dir = tempdir()
//
//      val error = shouldThrow<IllegalStateException> {
//        devMavenRepoFrom(metadataUrl(dir, "nowhere"))
//      }
//
//      error.message shouldContain "was recorded as 'nowhere'"
//    }
//  }

  context("escaping the repo path") {

    test("expect a quote to be escaped for Groovy") {
      val repo = devMavenRepo("com.example:lib:1.0.0", dir = tempdir().resolve("it's").apply { mkdirs() })

      devPublishRepository(repo, ScriptDsl.Groovy) shouldContain """/it\'s')"""
    }

    test("expect a dollar to be escaped for Kotlin") {
      val repo = devMavenRepo("com.example:lib:1.0.0", dir = tempdir().resolve("costs\$5").apply { mkdirs() })

      devPublishRepository(repo) shouldContain $$"""/costs${'$'}5")"""
    }
  }
})
