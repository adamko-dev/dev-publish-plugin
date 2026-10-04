@file:Suppress("UnstableApiUsage")

package dev.adamko.gradle.dev_publish.tasks

import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import dev.adamko.gradle.dev_publish.internal.DevPublishMetadata.REPO_CHECKSUM_FILE_NAME
import dev.adamko.gradle.dev_publish.internal.DevPublishMetadata.REPO_LOCATION_FILE_NAME
import dev.adamko.gradle.dev_publish.internal.checksums.repoChecksum
import javax.inject.Inject
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.work.DisableCachingByDefault

/**
 * Writes the location of the dev Maven repository into a properties file, so it can be put on a
 * test runtime classpath and read back by `devMavenRepo()` in `dev-publish-utils`.
 *
 * @see dev.adamko.gradle.dev_publish.internal.DevPublishMetadata.REPO_LOCATION_FILE_NAME
 * @see dev.adamko.gradle.dev_publish.internal.DevPublishMetadata.REPO_CHECKSUM_FILE_NAME
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
   * Every published file in [devMavenRepo].
   *
   * Metadata, checksums, and signatures are excluded,
   * because they are not deterministic (`maven-metadata.xml`, contains timestamps),
   * or are not relevant (DevPublish recomputes the checksum of artifacts, so checksums can be ignored).
   *
   * @see REPO_CHECKSUM_FILE_NAME
   */
  @get:InputFiles
  @get:PathSensitive(RELATIVE)
  @get:IgnoreEmptyDirectories
  @DevPublishInternalApi
  protected val devMavenRepoPublicationFiles: Provider<out FileCollection>
    get() = devMavenRepo.zip(excludedDevMavenRepoFilePatterns) { repoDir, exclusions ->
      repoDir.asFileTree
        .matching {
          exclude(exclusions)
        }
    }

  /**
   * Patterns of files to exclude from the dev Maven repository up-to-date check.
   * See [org.gradle.api.tasks.util.PatternFilterable] for syntax.
   */
  @get:Internal
  @DevPublishInternalApi
  abstract val excludedDevMavenRepoFilePatterns: SetProperty<String>

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
    val devMavenRepo = devMavenRepo.get().asFile.toPath()
    val devMavenRepoFiles = devMavenRepoPublicationFiles.orNull?.map { it.toPath() }.orEmpty()

    // delete output dir first, to clear any stale files
    outputDir.deleteRecursively()
    outputDir.createDirectories()

    outputDir.resolve(REPO_LOCATION_FILE_NAME)
      .writeText(devMavenRepoRelativeToMetadataDir.get())

    outputDir.resolve(REPO_CHECKSUM_FILE_NAME)
      .writeText(repoChecksum(devMavenRepo, devMavenRepoFiles))
  }
}
