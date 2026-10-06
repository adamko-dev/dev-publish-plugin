package dev.adamko.gradle.dev_publish.test_utils

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory

/**
 * The root of this repository.
 *
 * Tests run with a working directory of the module they live in, but the README and the
 * `examples/` directory live at the root, so walk up until they are found.
 */
val repoRootDir: Path by lazy {
  generateSequence(Path("").toAbsolutePath().normalize()) { it.parent }
    .firstOrNull { it.resolve("examples").isDirectory() }
    ?: error("could not find the repository root from ${Path("").toAbsolutePath()}")
}
