package com.example.tests

import dev.adamko.gradle.dev_publish.devMavenRepo
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.Test
import kotlin.test.assertTrue

class DevRepoTest {

  @Test
  fun `dev repo contains requested publications`() {
    val devRepo: Path = devMavenRepo()

    assertFileExists(devRepo.resolve("com/example/lib-extras/1.0.0/lib-extras-1.0.0.jar"))
    assertFileExists(devRepo.resolve("com/example/signed-library/1.0.0/signed-library-1.0.0.jar"))

    // lib-core is only reachable via lib-extras' devPublication dependency
    assertFileExists(devRepo.resolve("com/example/lib-core/1.0.0/lib-core-1.0.0.jar"))
  }

  @Test
  fun `dev repo contains no signatures when signing is skipped`() {
    val devRepo: Path = devMavenRepo()

    val versionDir = devRepo.resolve("com/example/signed-library/1.0.0")
    val signatures = versionDir.listDirectoryEntries("*.asc")

    assertTrue(signatures.isEmpty(), "expected no .asc signature files")
  }

  companion object {
    private fun assertFileExists(path: Path) {
      assertTrue(path.exists(), "expected file at $path, but it does not exist")
      assertTrue(path.isRegularFile(), "expected $path is a file, but it is not a file")
    }
  }
}
