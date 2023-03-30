package dev.adamko.gradle.dev_publish.tasks

import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import org.gradle.api.DefaultTask
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
) : DefaultTask() {

  /**
   * Input repo
   *
   * @see dev.adamko.gradle.dev_publish.DevPublishPluginExtension.stagingDevMavenRepo
   */
  @get:Internal
  abstract val stagingRepo: DirectoryProperty

  /**
   * [stagingRepo] is marked as [Internal] as a workaround for 'input directory does not exist' problem.
   *
   * https://docs.gradle.org/current/userguide/validation_problems.html#input_file_does_not_exist
   *
   * This property exists so Gradle will still be able to detect the inputs using [InputFiles].
   */
  @get:InputFiles
  @get:PathSensitive(RELATIVE)
  @DevPublishInternalApi
  protected val stagingRepoFiles: FileCollection
    get() = stagingRepo.asFileTree

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

  open fun from(files: Provider<Iterable<File>>) {
    repositoryContents.from(files)
  }

  @TaskAction
  fun updateDevRepo() {
    val syncResult = fs.sync {
      from(stagingRepo)
      from(repositoryContents)
      into(devRepo)
    }

    // clean up dir after syncing to prevent SNAPSHOT spam
    if (syncResult.didWork) {
      fs.delete {
        delete(stagingRepo)
      }
    }
  }
}
