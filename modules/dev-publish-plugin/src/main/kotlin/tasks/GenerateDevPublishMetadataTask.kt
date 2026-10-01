@file:Suppress("UnstableApiUsage")

package dev.adamko.gradle.dev_publish.tasks

import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import dev.adamko.gradle.dev_publish.internal.DevPublishMetadata.REPO_LOCATION_FILE_NAME
import javax.inject.Inject
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.work.DisableCachingByDefault

/**
 * Writes the location of the dev Maven repository into a properties file, so it can be put on a
 * test runtime classpath and read back by `devMavenRepo()` in `dev-publish-utils`.
 *
 * @see dev.adamko.gradle.dev_publish.internal.DevPublishMetadata.REPO_LOCATION_FILE_NAME
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

  @get:Internal
  abstract val rootProjectDir: DirectoryProperty

  /**
   * Directories containing checksum files for the dev Maven repository.
   *
   * Used to produce a checksum file for up-to-date check purposes.
   */
  @get:InputFiles
  @get:PathSensitive(RELATIVE)
  @get:IgnoreEmptyDirectories
  @DevPublishInternalApi
  abstract val devMavenRepoChecksumDirs: ConfigurableFileCollection

  /** Location of the dev Maven repository, relative to [outputDirectory]. */
  @get:Input
  val devMavenRepoRelativeToMetadataDir: Provider<String>
    get() = devMavenRepo.zip(outputDirectory.locationOnly) { devMavenRepo, metadataDir ->
      val baseDir = metadataDir.asFile.resolve(REPO_LOCATION_FILE_NAME).normalize().parentFile
      val repoDir = devMavenRepo.asFile
      val relative = repoDir.relativeToOrNull(baseDir)
        ?: error("devMavenRepo is not relative to metadataDir: $devMavenRepo, $metadataDir")
      relative.invariantSeparatorsPath
    }

  @TaskAction
  @DevPublishInternalApi
  protected fun generate() {
    val outputDir = outputDirectory.get().asFile.toPath()

    // delete output dir first, to clear any stale files
    outputDir.deleteRecursively()
    outputDir.createDirectories()

    outputDir.resolve(REPO_LOCATION_FILE_NAME)
      .writeText(devMavenRepoRelativeToMetadataDir.get())
  }
}
