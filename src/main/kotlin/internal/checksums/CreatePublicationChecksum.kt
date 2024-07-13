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
    val projectDir = parameters.projectDir.get().asFile

    val checksum = parameters.artifacts
      .map { artifact ->
        val artifactPath = artifact.relativeTo(projectDir).invariantSeparatorsPath
        "${artifactPath}$FileChecksumSeparator${artifact.checksum()}"
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
