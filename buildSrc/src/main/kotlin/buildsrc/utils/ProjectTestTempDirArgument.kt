package buildsrc.utils

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Internal
import org.gradle.kotlin.dsl.newInstance
import org.gradle.process.CommandLineArgumentProvider

/**
 * Passes the scratch directory that the functional tests generate their test projects into, to the
 * test JVM.
 *
 * A [CommandLineArgumentProvider], rather than `systemProperty(...)`, so the absolute path is not
 * tracked as a task input and test tasks stay relocatable.
 */
abstract class ProjectTestTempDirArgument internal constructor() : CommandLineArgumentProvider {

  /** Only ever written to by the tests, so it is not an input. */
  @get:Internal
  abstract val projectTestTempDir: DirectoryProperty

  override fun asArguments(): Iterable<String> = listOf(
    "-DprojectTestTempDir=${projectTestTempDir.get().asFile.invariantSeparatorsPath}",
  )

  companion object {
    fun ObjectFactory.ProjectTestTempDirArgument(
      configure: ProjectTestTempDirArgument.() -> Unit
    ): ProjectTestTempDirArgument =
      newInstance<ProjectTestTempDirArgument>().apply(configure)
  }
}