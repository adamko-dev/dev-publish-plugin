package dev.adamko.gradle.dev_publish.data

import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import dev.adamko.gradle.dev_publish.utils.FileChecksumSeparator
import dev.adamko.gradle.dev_publish.utils.checksum
import javax.inject.Inject
import org.gradle.api.Named
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE

/**
 * Specific information about a [MavenPublication] that will be used to create a checksum file.
 *
 * @param[named] must match the publication name, [MavenPublication.getName]
 */
abstract class PublicationData
@Inject
@DevPublishInternalApi
constructor(
  private val named: String
) : Named {

  /**
   * The artifacts inside a [MavenPublication]
   *
   * @see MavenPublication.getArtifacts
   */
  @get:InputFiles
  @get:PathSensitive(RELATIVE)
  abstract val artifacts: ConfigurableFileCollection

  /**
   * An artifact's GAV (group, artifact, version).
   *
   * @see MavenPublication.getArtifactId
   * @see MavenPublication.getGroupId
   * @see MavenPublication.getVersion
   */
  @get:Input
  abstract val identifier: Property<String>

  @Input
  override fun getName(): String = named

  @get:Internal
  internal val checksumFilename = "$named.txt"

  internal fun createChecksumContent(): String {
    val md5 = artifacts
      .map { "${it.invariantSeparatorsPath}${FileChecksumSeparator}${it.checksum()}" }
      .sorted()
      .joinToString("\n")

    val identifier = identifier.get()

    return /* language=TEXT */ """
      |$identifier
      |---
      |$md5
    """.trimMargin()
  }
}
