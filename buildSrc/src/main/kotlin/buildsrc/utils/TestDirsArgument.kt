package buildsrc.utils

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Internal
import org.gradle.kotlin.dsl.newInstance
import org.gradle.process.CommandLineArgumentProvider

/**
 * Passes machine-specific directories to the test JVM.
 *
 * A [CommandLineArgumentProvider], rather than `systemProperty(...)`, so the absolute paths are not
 * tracked as task inputs and test tasks stay relocatable.
 */
abstract class TestDirsArgument internal constructor() : CommandLineArgumentProvider {

  /** Scratch directory the functional tests generate their test projects into. Only ever written to. */
  @get:Internal
  abstract val projectTestTempDir: DirectoryProperty

  /** Gradle User Home of the host, whose dependency cache the tests reuse read-only. */
  @get:Internal
  abstract val hostGradleUserHome: DirectoryProperty

  override fun asArguments(): Iterable<String> = listOf(
    "-DprojectTestTempDir=${projectTestTempDir.get().asFile.invariantSeparatorsPath}",
    "-DhostGradleUserHome=${hostGradleUserHome.get().asFile.invariantSeparatorsPath}",
  )

  companion object {
    fun ObjectFactory.TestDirsArgument(
      configure: TestDirsArgument.() -> Unit
    ): TestDirsArgument =
      newInstance<TestDirsArgument>().apply(configure)
  }
}
