package dev.adamko.gradle.dev_publish.tasks

import dev.adamko.gradle.dev_publish.data.PublicationData
import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import dev.adamko.gradle.dev_publish.utils.info
import javax.inject.Inject
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault


@DisableCachingByDefault
abstract class GeneratePublicationDataChecksumTask
@Inject
@DevPublishInternalApi
constructor(
  private val files: FileSystemOperations,
) : BaseDevPublishTask() {

  /** Pertinent data for all present Maven publications. */
  @get:Nested
  abstract val publicationData: NamedDomainObjectContainer<PublicationData>

  /** @see dev.adamko.gradle.dev_publish.DevPublishPluginExtension.checksumsStore */
  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @get:LocalState
  @DevPublishInternalApi
  abstract val tempDir: DirectoryProperty

  /** Used for prettier log messages. */
  private val rootDir = project.rootDir

  @TaskAction
  @DevPublishInternalApi
  fun generate() {
    val tempDir = tempDir.get().asFile

    val publicationDataChecksumFile = tempDir.resolve("publication-data-checksum.txt").apply {
      parentFile.mkdirs()
    }

    val publicationsHash = publicationData.joinToString("\n") { data ->
      logger.info("Creating publication data checksum for ${data.name} ${data.artifacts.asPath}")
      data.createChecksumContent()
    }

    publicationDataChecksumFile.writeText(publicationsHash)

    logger.info {
      val path = publicationDataChecksumFile.relativeTo(rootDir)
      val content = publicationDataChecksumFile.readText().lines().joinToString(" // ")
      "created publication data checksum $path: $content"
    }

    files.sync {
      from(tempDir)
      into(outputDirectory)
    }
  }
}
