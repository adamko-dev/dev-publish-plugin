package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.GradleProjectTest
import dev.adamko.gradle.dev_publish.test_utils.build
import dev.adamko.gradle.dev_publish.test_utils.repoRootDir
import dev.adamko.gradle.dev_publish.devPublishRepository
import dev.adamko.gradle.dev_publish.internal.DevPublishVersion
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.paths.shouldBeADirectory
import io.kotest.matchers.paths.shouldBeAFile
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import kotlin.io.path.*
import org.gradle.testkit.runner.GradleRunner

/**
 * Builds every project in the `examples/` directory, so the code that the README shows is known to
 * work.
 *
 * Examples are discovered automatically, so a new example is covered by this test as soon as it is
 * added. The tasks to run are read from the `shell` code block in each example's `README.md`, so
 * the command this test runs is the same one the example tells a reader to run.
 *
 * Each example is copied into a temporary directory before being built, so that building it here
 * does not litter the checked-in example with `build/` and `.gradle/` directories. The copy is
 * deleted once the example has built successfully, and kept when it fails, so that it can be
 * inspected.
 */
class ExamplesTest : FunSpec({

  val examplesDir = repoRootDir.resolve("examples")

  /** Directories that must not be copied, nor compared - they are build output. */
  val generatedDirs = setOf("build", ".gradle", ".kotlin")

  val examples: List<Path> = examplesDir
    .listDirectoryEntries()
    .filter { it.isDirectory() }
    .sortedBy { it.name }

  test("expect some examples were discovered") {
    withClue("no example directories found in $examplesDir") {
      examples.shouldNotBeEmpty()
    }
  }

  /**
   * Reads the tasks from the command an example documents in its README, so that this test runs
   * exactly what the example tells a reader to run.
   */
  fun Path.documentedTasks(): List<String> {
    val readme = this / "README.md"
    readme.shouldBeAFile()

    val tasks = Regex("""```shell\s*\n\s*gradle (.+?)\s*\n\s*```""")
      .find(readme.readText())
      ?.groupValues
      ?.get(1)

    withClue(
      "${readme.relativeTo(examplesDir.parent)} must document the command to run, in a `shell` " +
          "code block, e.g. `gradle check`"
    ) {
      tasks.shouldNotBeNull()
    }

    return tasks.orEmpty().split(" ").filter { it.isNotBlank() }
  }

  /**
   * Point a copied example at the locally built DevPublish, instead of the Gradle Plugin Portal.
   *
   * The checked-in examples are exactly what a reader should copy, so they carry none of this: they
   * resolve DevPublish the way any other project would. Only the copy this test builds is
   * redirected at `build/test-maven-repo`.
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
      "could not redirect ${settingsFile.relativeTo(examplesDir.parent)} at the local DevPublish " +
          "repo - has the example's settings file changed shape?"
    }

    settingsFile.writeText(redirected)
  }

  /**
   * Build a copied example against the DevPublish built here, not the released version it pins.
   *
   * The checked-in examples name a real, released version, because that is what a reader should
   * copy. Only the copy this test builds is moved onto [DevPublishVersion].
   */
  fun Path.useTestDevPublishVersion() {
    val pluginVersion = Regex("""(id\("dev\.adamko\.dev-publish"\)\s+version\s+)"[^"]+"""")

    val rewritten = walk()
      .filter { it.name.endsWith(".gradle.kts") }
      .count { buildFile ->
        val original = buildFile.readText()
        val updated = original.replace(pluginVersion, """$1"$DevPublishVersion"""")
        (updated != original).also { changed -> if (changed) buildFile.writeText(updated) }
      }

    check(rewritten > 0) {
      "could not find a DevPublish plugin version to replace in $this - has the example changed " +
          "how it declares the plugin?"
    }
  }

  /** Copies an example, skipping build output, so it can be built somewhere disposable. */
  fun Path.copyExampleTo(target: Path) {
    target.deleteRecursively()
    target.createDirectories()

    walk()
      .filter { file -> generatedDirs.none { it in file.relativeTo(this@copyExampleTo).map(Path::name) } }
      .filter { it.isRegularFile() }
      .forEach { source ->
        val destination = target / source.relativeTo(this@copyExampleTo)
        destination.parent.createDirectories()
        source.copyTo(destination, overwrite = true)
      }
  }

  examples.forEach { example ->
    context(example.name) {
      val tasks = example.documentedTasks()

      val projectDir = GradleProjectTest.projectTestTempDir / "examples" / example.name
      example.copyExampleTo(projectDir)
      projectDir.addLocalDevPublishRepo()
      projectDir.useTestDevPublishVersion()

      val runner = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
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
