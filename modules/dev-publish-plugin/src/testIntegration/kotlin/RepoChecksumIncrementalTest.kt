package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestCaseOrder
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.io.path.*

/**
 * An incremental run of `generateDevPublishMetadata` must give the same checksum as a full run.
 *
 * The files share one directory, so per-file state can't be keyed by directory.
 * Renaming files must not change the checksum.
 */
class RepoChecksumIncrementalTest : FunSpec({

  context("when one file in a shared directory changes, then is restored") {
    val project = project()

    RepoFiles.forEach { repoFile ->
      test("expect modifying and restoring $repoFile gives the full-run checksum") {
        val file = project.projectDir.resolve("$RepoDir/$repoFile")
        val original = file.readText()

        project.runGenerate()
        file.writeText("modified")
        project.runGenerate()
        file.writeText(original)
        project.runGenerate()
        val incremental = project.repoChecksum()

        project.runGenerate("--rerun")
        project.repoChecksum() shouldBe incremental
      }

      test("expect deleting and restoring $repoFile gives the full-run checksum") {
        val file = project.projectDir.resolve("$RepoDir/$repoFile")
        val original = file.readText()

        project.runGenerate()
        file.deleteExisting()
        project.runGenerate()
        file.writeText(original)
        project.runGenerate()
        val incremental = project.repoChecksum()

        project.runGenerate("--rerun")
        project.repoChecksum() shouldBe incremental
      }
    }
  }

  context("when one file in a shared directory changes, in a full run") {
    val project = project()

    // full runs, because an incremental run only re-hashes the changed file
    RepoFiles.forEach { repoFile ->
      test("expect modifying $repoFile changes the checksum") {
        val file = project.projectDir.resolve("$RepoDir/$repoFile")
        val original = file.readText()

        project.runGenerate("--rerun")
        val before = project.repoChecksum()

        file.writeText("modified")
        project.runGenerate("--rerun")
        val after = project.repoChecksum()

        file.writeText(original)
        after shouldNotBe before
      }
    }
  }

  context("when every file moves to another directory") {
    val project = project()

    // move every file, so only the directory changes, not which contents are present
    test("expect the checksum changes") {
      project.runGenerate()
      val before = project.repoChecksum()

      RepoFiles.forEach { repoFile ->
        project.projectDir.resolve("$RepoDir/$repoFile").moveTo(
          project.projectDir.resolve("fake-repo/com/example/lib/2.0.0/$repoFile")
            .createParentDirectories()
        )
      }
      project.runGenerate()

      project.repoChecksum() shouldNotBe before
    }
  }

  context("when two files in different directories have the same contents") {
    val project = project()

    // like empty javadoc jars from two modules
    val libJavadoc = project.projectDir.resolve("$RepoDir/lib-1.0.0-javadoc.jar")
    val otherJavadoc = project.projectDir.resolve("fake-repo/com/example/other/1.0.0/other-1.0.0-javadoc.jar")
    listOf(libJavadoc, otherJavadoc).forEach { file ->
      file.createParentDirectories().writeText("empty javadoc")
    }

    // only contents change, so the file set is unchanged and the second run is incremental
    test("expect modifying one of them gives the full-run checksum") {
      project.runGenerate()
      otherJavadoc.writeText("modified")
      project.runGenerate()
      val incremental = project.repoChecksum()

      project.runGenerate("--rerun")
      project.repoChecksum() shouldBe incremental
    }

    test("expect changing a file to match another file's contents gives the full-run checksum") {
      project.runGenerate()
      otherJavadoc.writeText(libJavadoc.readText())
      project.runGenerate()
      val incremental = project.repoChecksum()

      project.runGenerate("--rerun")
      project.repoChecksum() shouldBe incremental
    }
  }

  context("when every file is renamed, like a SNAPSHOT re-publish") {
    val project = project()

    // several renames, so a checksum that depends on the order of file names is unlikely to pass by chance
    listOf(
      "20261004.120000-1",
      "20261004.130000-2",
      "20261004.140000-3",
    ).forEach { snapshotVersion ->
      test("expect renaming the files to $snapshotVersion keeps the checksum") {
        project.runGenerate()
        val before = project.repoChecksum()

        val dir = project.file(RepoDir)
        dir.listDirectoryEntries().forEach { file ->
          file.moveTo(dir.resolve("lib-1.0.0-$snapshotVersion.${file.name.substringAfterLast('.')}"))
        }

        project.runGenerate()
        project.repoChecksum() shouldBe before
      }
    }
  }
}) {

  override fun testCaseOrder(): TestCaseOrder = TestCaseOrder.Sequential

  companion object {
    private const val RepoDir = "fake-repo/com/example/lib/1.0.0"

    private val RepoFiles = listOf(
      "lib-1.0.0.jar",
      "lib-1.0.0.pom",
      "lib-1.0.0.module",
    )

    private fun GradleProjectTest.runGenerate(vararg args: String) {
      runner.withArguments(":generateDevPublishMetadata", *args).build()
    }

    private fun GradleProjectTest.repoChecksum(): String =
      projectDir.resolve("build/tmp/.maven-dev/metadata/dev.publish.metadata.repo-checksum.txt").readText()

    private fun TestScope.project(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "repo-checksum-incremental",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {
        buildGradleKts = """
          |plugins {
          |  id("dev.adamko.dev-publish") version "+"
          |}
          |
          |tasks.generateDevPublishMetadata {
          |  // a hand-made repo, so the test controls every file
          |  devMavenRepo.set(layout.projectDirectory.dir("fake-repo"))
          |}
          |""".trimMargin()

        // remove files renamed by a previous run
        file("fake-repo").deleteRecursively()
        RepoFiles.forEach { repoFile ->
          createFile("$RepoDir/$repoFile", "content of $repoFile")
        }
      }
  }
}
