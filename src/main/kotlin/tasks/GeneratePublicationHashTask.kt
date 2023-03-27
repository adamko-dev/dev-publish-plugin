package dev.adamko.gradle.dev_publish.tasks

import dev.adamko.gradle.dev_publish.data.PublicationData
import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.*
import javax.inject.Inject


@CacheableTask
abstract class GeneratePublicationHashTask
@Inject
constructor(
  private val files: FileSystemOperations,
) : DefaultTask() {

  /** Pertinent data for all present Maven publications. */
  @get:Nested
  abstract val publicationData: NamedDomainObjectContainer<PublicationData>

  /** @see dev.adamko.gradle.dev_publish.DevPublishPluginExtension.checksumsStore */
  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @get:LocalState
  @DevPublishInternalApi
  abstract val tempDir: DirectoryProperty

  /** Used for prettier log messages */
  private val rootDir = project.rootDir

  @TaskAction
  fun generate() {
    files.delete { delete(tempDir) }

    publicationData.forEach { data ->
      logger.info("Creating publication data checksum for ${data.name} ${data.artifacts.asPath}")

      val file = tempDir.file(data.checksumFilename).get().asFile.apply {
        if (!exists()) {
          parentFile.mkdirs()
          createNewFile()
        }
        writeText(data.createChecksumContent())
      }

      logger.info("created checksum ${file.relativeTo(rootDir)}: ${file.readText().lines().joinToString(" // ")}")
    }

    files.sync {
      from(tempDir)
      into(outputDirectory)
    }
  }
}
