package dev.adamko.gradle.dev_publish.tasks

import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import javax.inject.Inject
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.ABSOLUTE
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Collects the publications of this project into [publicationsStore].
 *
 * Only the current publications are collected,
 * so a removed publication's files are deleted from [publicationsStore].
 */
@DisableCachingByDefault(because = "This task only relocates files")
abstract class PublishAllToDevRepoTask
@Inject
internal constructor(
  private val fs: FileSystemOperations,
) : BaseDevPublishTask() {

  /** @see dev.adamko.gradle.dev_publish.DevPublishPluginExtension.publicationsStore */
  @get:OutputDirectory
  @DevPublishInternalApi
  abstract val publicationsStore: DirectoryProperty

  /** The repo written by each dev repo publish task, in a directory named after the task. */
  @get:InputFiles
  // Absolute, because directory names are used in the output.
  // Task is not cached, so relocation doesn't matter.
  @get:PathSensitive(ABSOLUTE)
  @DevPublishInternalApi
  abstract val publicationDirs: ConfigurableFileCollection

  @TaskAction
  @DevPublishInternalApi
  protected fun collectPublications() {
    fs.sync {
      publicationDirs.forEach { dir ->
        from(dir) {
          into(dir.name)
        }
      }
      into(publicationsStore)
      includeEmptyDirs = false
    }
  }
}
