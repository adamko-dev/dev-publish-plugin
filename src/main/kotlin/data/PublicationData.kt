package dev.adamko.gradle.dev_publish.data

import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
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
 * @param[name] must match the publication name, [MavenPublication.getName]
 */
abstract class PublicationData
@Inject
@DevPublishInternalApi
constructor(
  private val name: String,
) : Named {

  /**
   * The Gradle Module Metadata files that describe this publication.
   *
   * Typically, there should only be one GMM file, but the Gradle API does not have a stable way to access it.
   * Therefore, we accept multiple files, in case this assumption will change in the future.
   *
   * @see MavenPublication.getArtifacts
   */
  @get:InputFiles
  @get:PathSensitive(RELATIVE)
  abstract val gradleModuleMetadata: ConfigurableFileCollection

  /**
   * The artifacts inside a [MavenPublication].
   *
   * This property is no longer used. Instead, the data inside the Gradle Module Metadata files is used.
   * These files contain all details (including checksums) of the artifacts attached to the publication.
   *
   * @see MavenPublication.getArtifacts
   */
  @get:Internal
  @Deprecated("No longer used. Scheduled for removal in version 2.0.0.")
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

  @get:Internal
  internal val checksumFilename: String get() = "$name.txt"

  @Input
  override fun getName(): String = name
}
