package dev.adamko.gradle.dev_publish.internal.checksums

import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo
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
    val projectDir = parameters.projectDir.get().asFile.toPath()
    val gradleModuleMetadata = parameters.gradleModuleMetadata.map { it.toPath() }

    return gradleModuleMetadata
      .map { gmm ->
        val gmmPath = gmm.relativeTo(projectDir).invariantSeparatorsPathString
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
