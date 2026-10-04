package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.internal.checksums.repoChecksum
import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class RepoChecksumTest : FunSpec({

  val snapshotDir = "com/example/lib/1.0.0-SNAPSHOT"

  test("expect renaming files does not change the checksum") {
    val first = mockRepo(
      "$snapshotDir/lib-1.0.0-20261003.140147-1.jar" to "jar",
      "$snapshotDir/lib-1.0.0-20261003.140147-1.pom" to "pom",
    )
    val republished = mockRepo(
      "$snapshotDir/lib-1.0.0-20261003.140248-1.jar" to "jar",
      "$snapshotDir/lib-1.0.0-20261003.140248-1.pom" to "pom",
    )

    first.checksum() shouldBe republished.checksum()
  }

  test("expect the order of the files does not change the checksum") {
    val repo = mockRepo(
      "$snapshotDir/lib.jar" to "jar",
      "$snapshotDir/lib.pom" to "pom",
    )

    repoChecksum(repo.dir, repo.files) shouldBe repoChecksum(repo.dir, repo.files.reversed())
  }

  test("expect changing a file's content changes the checksum") {
    val before = mockRepo("$snapshotDir/lib.jar" to "v1")
    val after = mockRepo("$snapshotDir/lib.jar" to "v2")

    before.checksum() shouldNotBe after.checksum()
  }

  test("expect moving a file to another directory changes the checksum") {
    val before = mockRepo("com/example/lib/1.0.0/lib.jar" to "jar")
    val after = mockRepo("com/example/lib/1.0.1/lib.jar" to "jar")

    before.checksum() shouldNotBe after.checksum()
  }

  test("expect adding a file changes the checksum") {
    val before = mockRepo(
      "$snapshotDir/lib.jar" to "jar",
    )
    val after = mockRepo(
      "$snapshotDir/lib.jar" to "jar",
      "$snapshotDir/lib-sources.jar" to "sources",
    )

    before.checksum() shouldNotBe after.checksum()
  }
}) {

  private class MockRepo(val dir: Path, val files: List<Path>) {
    fun checksum(): String = repoChecksum(dir, files)
  }

  companion object {
    /** A repo containing [files], as `path to content`. */
    private fun TestConfiguration.mockRepo(vararg files: Pair<String, String>): MockRepo {
      val dir = tempdir().toPath()
      val written = files.map { (path, content) ->
        dir.resolve(path).apply {
          parent.createDirectories()
          writeText(content)
        }
      }
      return MockRepo(dir, written)
    }
  }
}
