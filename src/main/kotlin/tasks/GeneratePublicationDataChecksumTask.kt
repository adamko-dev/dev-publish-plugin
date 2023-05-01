package dev.adamko.gradle.dev_publish.tasks

import dev.adamko.gradle.dev_publish.data.PublicationData
import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import dev.adamko.gradle.dev_publish.services.DevPublishService
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject


@DisableCachingByDefault
abstract class GeneratePublicationDataChecksumTask
@Inject
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

  // This property provides access to the service instance. It should be
  // Property<DevPublishService>
  // but because of a bug in Gradle we have to use a more generic type.
  // See https://github.com/gradle/gradle/issues/17559
  @get:Internal
  internal abstract val devPublishServiceProperty: Property<Any>

  @get:Internal
  internal val devPublishService: Provider<DevPublishService>
    @Suppress("UNCHECKED_CAST")
    get() = devPublishServiceProperty as Provider<DevPublishService>

  /** Used for prettier log messages */
  private val rootDir = project.rootDir

  @TaskAction
  fun generate() {
    files.delete { delete(tempDir) }

    val publicationsHash = publicationData.joinToString("\n") { data ->
      logger.info("Creating publication data checksum for ${data.name} ${data.artifacts.asPath}")
      data.createChecksumContent()
    }

    val file = tempDir.file("publication-data-checksum.txt").get().asFile.apply {
      if (!exists()) {
        parentFile.mkdirs()
        createNewFile()
      }
      writeText(publicationsHash)
    }

    logger.debug(
      "created publication data checksum {}: {}",
      file.relativeTo(rootDir),
      file.readText().lines().joinToString(" // ")
    )

    files.sync {
      from(tempDir)
      into(outputDirectory)
    }
  }
}
