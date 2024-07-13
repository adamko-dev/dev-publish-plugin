package dev.adamko.gradle.dev_publish.data

import dev.adamko.gradle.dev_publish.utils.checksum
import java.io.File
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.*
import org.gradle.kotlin.dsl.of


internal abstract class LoadPublicationChecksum : ValueSource<String, LoadPublicationChecksum.Parameters> {

  interface Parameters : ValueSourceParameters {
    val checksumFilename: Property<String>
    val checksumsStore: DirectoryProperty
  }

  override fun obtain(): String? {
    val checksumFilename = parameters.checksumFilename.orNull ?: return null
    val checksumStore = parameters.checksumsStore.get().asFile

    return checksumStore
      .resolve(checksumFilename)
      .takeIf(File::exists)
      ?.readText()
  }

  internal companion object {
    fun ProviderFactory.loadPublicationChecksum(
      configure: Parameters.() -> Unit,
    ): Provider<String> =
      of(LoadPublicationChecksum::class) {
        parameters(configure)
      }
  }
}


internal abstract class CreatePublicationChecksum : ValueSource<String, CreatePublicationChecksum.Parameters> {

  interface Parameters : ValueSourceParameters {
    val projectDir: DirectoryProperty
    val artifacts: ConfigurableFileCollection
    val identifier: Property<String>
  }

  override fun obtain(): String? {
    val projectDir = parameters.projectDir.get().asFile

    val checksum = parameters.artifacts
      .map { artifact ->
        val artifactPath = artifact.relativeTo(projectDir).invariantSeparatorsPath
        "${artifactPath}${FileChecksumSeparator}${artifact.checksum()}"
      }
      .sorted()
      .joinToString("\n")

    val identifier = parameters.identifier.get()

    return /* language=TEXT */ """
      |$identifier
      |---
      |$checksum
    """.trimMargin()
  }

  internal companion object {
    @Suppress("ConstPropertyName")
    const val FileChecksumSeparator = ":"

    fun ProviderFactory.createPublicationChecksum(
      configure: Parameters.() -> Unit,
    ): Provider<String> =
      of(CreatePublicationChecksum::class) {
        parameters(configure)
      }
  }
}
