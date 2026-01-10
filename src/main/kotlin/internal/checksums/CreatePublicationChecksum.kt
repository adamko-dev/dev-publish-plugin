package dev.adamko.gradle.dev_publish.internal.checksums

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.*
import org.gradle.kotlin.dsl.of

internal abstract class CreatePublicationChecksum : ValueSource<String, CreatePublicationChecksum.Parameters> {

  interface Parameters : ValueSourceParameters {
    val projectDir: DirectoryProperty
    val artifacts: ConfigurableFileCollection
    val identifier: Property<String>
  }

  override fun obtain(): String? {
    val identifier = parameters.identifier.get()
    val artifactsChecksums = artifactsChecksums()

    return buildString {
      appendLine(identifier)
      appendLine("---")
      artifactsChecksums.forEach {
        appendLine(it)
      }
    }.trim()
  }

  private fun artifactsChecksums(): List<String> {
    val projectDir = parameters.projectDir.get().asFile

    return parameters.artifacts
      .map { artifact ->
        val artifactPath = artifact.relativeTo(projectDir).invariantSeparatorsPath
        "${artifactPath}$FileChecksumSeparator${artifact.checksum()}"
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
