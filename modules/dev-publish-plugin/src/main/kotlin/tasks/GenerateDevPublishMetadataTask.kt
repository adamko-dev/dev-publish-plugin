package dev.adamko.gradle.dev_publish.tasks

import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import dev.adamko.gradle.dev_publish.internal.DevPublishMetadata
import javax.inject.Inject
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Writes the location of the dev Maven repository into a properties file, so it can be put on a
 * test runtime classpath and read back by [dev.adamko.gradle.dev_publish.devMavenRepo].
 *
 * @see dev.adamko.gradle.dev_publish.internal.DevPublishMetadata.DEV_MAVEN_REPO_KEY
 */
@DisableCachingByDefault(because = "Not worth caching")
@DevPublishInternalApi
abstract class GenerateDevPublishMetadataTask
@Inject
@DevPublishInternalApi
constructor() : BaseDevPublishTask() {

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  /** @see dev.adamko.gradle.dev_publish.DevPublishPluginExtension.devMavenRepo */
  @get:Internal
  abstract val devMavenRepo: DirectoryProperty

  /**
   * Location of the dev Maven repository, relative to [outputDirectory].
   *
   * Only track the location of [devMavenRepo], not the content, to improve cache hits.
   */
  @get:Input
  val devMavenRepoRelativeToMetadataDir: Provider<String>
    get() = devMavenRepo.zip(outputDirectory.locationOnly) { repo, root ->
      val relative = repo.asFile.relativeToOrNull(root.asFile)
        ?: error(
          "The dev Maven repository ${repo.asFile} has no path relative to ${root.asFile}, " +
              "so it cannot be recorded. Set devPublish.devMavenRepo to a location under the " +
              "same root as the build directory."
        )
      relative.invariantSeparatorsPath
    }

  @TaskAction
  @DevPublishInternalApi
  fun generate() {
    val metadataFile = outputDirectory.get().asFile.toPath().resolve(DevPublishMetadata.METADATA_FILE_PATH)
    DevPublishMetadata.write(
      file = metadataFile,
      devMavenRepo = devMavenRepoRelativeToMetadataDir.get(),
    )
  }
}
