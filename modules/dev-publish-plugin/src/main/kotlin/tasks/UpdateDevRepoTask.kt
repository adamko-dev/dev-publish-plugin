package dev.adamko.gradle.dev_publish.tasks

import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import dev.adamko.gradle.dev_publish.utils.dropDirectory
import java.io.File
import javax.inject.Inject
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "This task only relocates files")
abstract class UpdateDevRepoTask
@Inject
@DevPublishInternalApi
constructor(
  private val fs: FileSystemOperations,
) : BaseDevPublishTask() {

  /**
   * Additional files to include in [devRepo].
   *
   * The additional files are typically publications sourced from other subprojects.
   */
  @get:InputFiles
  @get:PathSensitive(RELATIVE)
  abstract val repositoryContents: ConfigurableFileCollection

  /**
   * Output dev-repo
   *
   * @see dev.adamko.gradle.dev_publish.DevPublishPluginExtension.devMavenRepo
   */
  @get:OutputDirectory
  abstract val devRepo: DirectoryProperty

  //region deprecated
  /**
   * Input repo.
   *
   * @see dev.adamko.gradle.dev_publish.DevPublishPluginExtension.stagingDevMavenRepo
   */
  @get:Internal
  @Deprecated("No longer used: repositories are collected using configurations. Scheduled for removal in version 2.0.")
  abstract val publicationsStore: DirectoryProperty

  /** @see repositoryContents */
  @Deprecated(
    "This helper function will be removed and can be replaced with adding files into `repositoryContents`",
    ReplaceWith("repositoryContents.from(files)"),
  )
  open fun from(files: Provider<Iterable<File>>) {
    repositoryContents.from(files)
  }
  //endregion

  @TaskAction
  @DevPublishInternalApi
  fun updateDevRepo() {
    fs.sync {
      from(repositoryContents) {
        eachFile {
          relativePath = relativePath.dropDirectory()
        }
      }

      into(devRepo)

      includeEmptyDirs = false
    }
  }
}
