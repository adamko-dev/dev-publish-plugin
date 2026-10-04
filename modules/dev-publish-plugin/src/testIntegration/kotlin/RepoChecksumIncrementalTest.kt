package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestCaseOrder
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
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
