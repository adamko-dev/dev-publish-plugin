package dev.adamko.gradle.dev_publish.internal.checksums

import java.io.File
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
      ?.trim()
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
