package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.GradleProjectTest
import dev.adamko.gradle.dev_publish.test_utils.build
import dev.adamko.gradle.dev_publish.test_utils.repoRootDir
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.paths.shouldBeAFile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import kotlin.io.path.*
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion

/**
 * Builds every project in `examples/`, so the code the README shows is known to work.
 *
 * Examples are discovered automatically, and the tasks to run are read from the `shell` code block
 * in each example's `README.md`, so this runs the same command the example tells a reader to run.
 */
class ExamplesTest : FunSpec({

  val examplesDir = repoRootDir.resolve("examples")

  /** Directories that must not be copied, nor compared - they are build output. */
  val generatedDirs = setOf("build", ".gradle", ".kotlin")

  val examples: List<Path> = examplesDir
    .listDirectoryEntries()
    .filter { it.isDirectory() }
    .sortedBy { it.name }

  /** The start of the line an example uses to pin Dev Publish. */
  val versionPin = """id("dev.adamko.dev-publish") version """

  /** What an example must pin: the version this build publishes, as it will be released. */
  val expectedVersion = GradleProjectTest.devPublishVersion.substringBefore("-SNAPSHOT")

  /** Every Dev Publish version an example pins, with the file that pins it. */
  fun pinnedVersions(): List<Pair<Path, String>> =
    examplesDir.walk()
      .filter { file -> generatedDirs.none { it in file.map(Path::name) } }
      .filter { it.name.endsWith(".gradle.kts") }
      .flatMap { file ->
        file.readLines()
          .map { it.trim() }
          .filter { it.startsWith(versionPin) }
          .map { file to it.substringAfter("$versionPin\"").substringBefore("\"") }
      }
      .toList()

  test("expect some examples were discovered") {
    withClue("no example directories found in $examplesDir") {
      examples.shouldNotBeEmpty()
    }
  }

  test("expect every example pins the version this build publishes") {
    val pins = pinnedVersions()

    withClue("no example pins a Dev Publish version - has the plugins block changed shape?") {
      pins.shouldNotBeEmpty()
    }

    pins.forEach { (file, pinned) ->
      withClue(file.relativeTo(examplesDir.parent).invariantSeparatorsPathString) {
        pinned shouldBe expectedVersion
      }
    }
  }

  /**
   * Reads the tasks from the command an example documents in its README, so that this test runs
   * exactly what the example tells a reader to run.
   */
  fun findDocumentedTasks(dir: Path): List<String> {
    val readme = dir.resolve("README.md")
    readme.shouldBeAFile()

    val tasks = Regex("""```shell\s*\n\s*gradle (.+?)\s*\n\s*```""")
      .find(readme.readText())
      ?.groupValues
      ?.get(1)

    withClue(
      readme.relativeTo(examplesDir.parent).invariantSeparatorsPathString +
          " must document the command to run, in a `shell` code block, e.g. `gradle check`"
    ) {
      tasks.shouldNotBeNull()
    }

    return tasks.orEmpty().split(" ").filter { it.isNotBlank() }
  }

  /**
   * Point a copied example at the locally built Dev Publish, instead of the Gradle Plugin Portal.
   *
   * The checked-in examples are exactly what a reader should copy, so they carry none of this: they
   * resolve Dev Publish the way any other project would. Only the copy this test builds is
   * redirected at the dev Maven repo.
   */
  fun Path.addLocalDevPublishRepo() {
    val settingsFile = this / "settings.gradle.kts"
    val localRepo = devPublishRepository(Path(GradleProjectTest.testMavenRepoPathString))
      .prependIndent("    ")

    val settings = settingsFile.readText()

    // the plugin itself, and - for examples that use the test-kit helpers - the plugin jar
    val redirected = settings
      .replace("    gradlePluginPortal()", "$localRepo\n    gradlePluginPortal()")
      .replace("    mavenCentral()", "$localRepo\n    mavenCentral()")

    check(redirected != settings) {
      "could not redirect ${settingsFile.relativeTo(examplesDir.parent)} at the local Dev Publish" +
          "repo - has the example's settings file changed shape?"
    }

    settingsFile.writeText(redirected)
  }

  /**
   * Build a copied example against the Dev Publish built here, not the released version it pins.
   *
   * The checked-in examples name a real, released version, because that is what a reader should
   * copy.
   */
  fun useTestDevPublishVersion(projectDir: Path) {
    projectDir.walk()
      .filter { it.name.endsWith(".gradle.kts") }
      .forEach { buildFile ->
        buildFile.writeText(
          buildFile.useLines { lines ->
            lines.joinToString("\n") { line ->
              // only the version literal, so the indentation and any `apply false` survive
              if (line.trim().startsWith(versionPin)) {
                val pinned = line.substringAfter("$versionPin\"").substringBefore("\"")
                line.replace("\"$pinned\"", "\"+\"")
              } else {
                line
              }
            }
          }
        )
      }
  }

  /** Copies an example, skipping build output, so it can be built somewhere disposable. */
  fun copyExampleTo(projectDir: Path, target: Path) {
    target.deleteRecursively()
    target.createDirectories()

    projectDir.walk()
      .filter { file -> generatedDirs.none { it in file.relativeTo(projectDir).map(Path::name) } }
      .filter { it.isRegularFile() }
      .forEach { source ->
        val destination = target / source.relativeTo(projectDir)
        destination.parent.createDirectories()
        source.copyTo(destination, overwrite = true)
      }
  }

  /**
   * A Kotlin Gradle plugin must use `embeddedKotlinVersion`, but Gradle 8 embeds Kotlin 2.0,
   * which can't read the Kotlin 2.2 metadata of `dev-publish-utils`.
   */
  val requiresGradle9 = setOf("gradle-plugin-testkit")

  val isGradle8 = GradleProjectTest.testedGradleVersion < GradleVersion.version("9.0")

  examples.forEach { example ->
    context(example.name).config(enabled = !(isGradle8 && example.name in requiresGradle9)) {
      val tasks = findDocumentedTasks(example)

      val gradleVersion = GradleProjectTest.testedGradleVersion.version
      val projectDir = GradleProjectTest.projectTestTempDir / "examples" / gradleVersion / example.name
      copyExampleTo(example, projectDir)
      projectDir.addLocalDevPublishRepo()
      useTestDevPublishVersion(projectDir)

      val runner = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withGradleVersion(gradleVersion)
        .forwardOutput()
        .withArguments(tasks)

      test("expect `gradle ${tasks.joinToString(" ")}` succeeds") {
        runner.build {
          output shouldContain "SUCCESSFUL"
        }
      }

      test("expect the configuration cache is reused when built again") {
        runner.build {
          output shouldContain "Configuration cache entry reused"
        }

        // only clean up if the example built successfully, so failures can be inspected
        projectDir.deleteRecursively()
      }
    }
  }
})
