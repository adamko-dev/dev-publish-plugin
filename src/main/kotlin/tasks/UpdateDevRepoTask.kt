package dev.adamko.gradle.dev_publish.tasks

import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import dev.adamko.gradle.dev_publish.utils.dropDirectory
import org.gradle.api.file.*
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class UpdateDevRepoTask
@Inject
constructor(
  private val fs: FileSystemOperations,
) : BaseDevPublishTask() {

  /**
   * Input repo
   *
   * @see dev.adamko.gradle.dev_publish.DevPublishPluginExtension.stagingDevMavenRepo
   */
  @get:Internal
  abstract val publicationsStore: DirectoryProperty

  /**
   * [publicationsStore] is marked as [Internal] as a workaround for 'input directory does not exist' problem.
   *
   * https://docs.gradle.org/current/userguide/validation_problems.html#input_file_does_not_exist
   *
   * This property exists so Gradle will still be able to detect the inputs using [InputFiles].
   */
  @get:InputFiles
  @get:PathSensitive(RELATIVE)
  @DevPublishInternalApi
  protected val publicationsStoreFiles: FileCollection
    get() = publicationsStore.asFileTree

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

  /** @see repositoryContents */
  open fun from(files: Provider<Iterable<File>>) {
    repositoryContents.from(files)
  }

  @TaskAction
  fun updateDevRepo() {
    fs.sync {
      from(publicationsStore) {
        eachFile {
          relativePath = relativePath.dropDirectory()
        }
      }
      from(repositoryContents)

      into(devRepo)

      includeEmptyDirs = false
    }
  }
}
