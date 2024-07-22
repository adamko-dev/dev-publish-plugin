package dev.adamko.gradle.dev_publish.tasks

import dev.adamko.gradle.dev_publish.data.PublicationData
import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import dev.adamko.gradle.dev_publish.internal.checksums.CreatePublicationChecksum.Companion.createPublicationChecksum
import dev.adamko.gradle.dev_publish.utils.info
import javax.inject.Inject
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault


@DisableCachingByDefault(because = "Always re-compute checksums")
abstract class GeneratePublicationDataChecksumTask
@Inject
@DevPublishInternalApi
constructor(
  private val fs: FileSystemOperations,
  private val layout: ProjectLayout,
  private val providers: ProviderFactory,
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

    tempDir.deleteRecursively()
    tempDir.mkdirs()

    val currentProjectDir = layout.projectDirectory

    publicationData.forEach { data ->
      logger.info("Creating publication data checksum for ${data.name} ${data.artifacts.asPath}")

      val checksumFile = tempDir.resolve(data.checksumFilename)

      val checksum = providers.createPublicationChecksum {
        this.projectDir.set(currentProjectDir)
        this.artifacts.from(data.artifacts)
        this.identifier.set(data.identifier)
      }.get()

      checksumFile.writeText(checksum)

      logger.info {
        val path = checksumFile.relativeTo(rootDir)
        val content = checksumFile.readText().lines().joinToString(" \\n ")
        "created publication ${data.name} checksum $path:\n$content"
      }
    }

    fs.sync {
      from(tempDir)
      into(outputDirectory)
    }
  }
}
