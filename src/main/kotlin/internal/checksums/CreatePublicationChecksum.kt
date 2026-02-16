package dev.adamko.gradle.dev_publish.internal.checksums

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.*
import org.gradle.kotlin.dsl.of

internal abstract class CreatePublicationChecksum : ValueSource<String, CreatePublicationChecksum.Parameters> {

  interface Parameters : ValueSourceParameters {
    val projectDir: DirectoryProperty
    val identifier: Property<String>
    val gradleModuleMetadata: ConfigurableFileCollection
  }

  override fun obtain(): String? {
    val identifier = parameters.identifier.get()
    val gradleModuleMetadataChecksums = gradleModuleMetadataChecksums()

    return buildString {
      appendLine(identifier)
      appendLine("---")
      gradleModuleMetadataChecksums.forEach {
        appendLine(it)
      }
    }.trim()
  }

  private fun gradleModuleMetadataChecksums(): List<String> {
    return parameters.gradleModuleMetadata
      .map { gmm ->
        val gmmPath = gmm.relativeTo(parameters.projectDir.get().asFile).invariantSeparatorsPath
        "${gmmPath}$FileChecksumSeparator${gmm.checksum()}"
      }
      .sorted()
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
