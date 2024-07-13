package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import org.gradle.api.file.DirectoryProperty

/**
 * Settings for controlling the behaviour of [DevPublishPlugin] and its tasks.
 */
abstract class DevPublishPluginExtension
@DevPublishInternalApi constructor() {

  /**
   * Location of the file-based test Maven repository.
   *
   * The repository is specific per subproject and should not be referenced by other subprojects.
   *
   * This property should be passed into tests and provided as a Maven repository.
   */
  abstract val devMavenRepo: DirectoryProperty

  /**
   * Location of temporary Maven repository, that will be used to populate [devMavenRepo].
   *
   * This value is intended for internal use and should not typically be configured in build scripts.
   */
  @DevPublishInternalApi
  abstract val stagingDevMavenRepo: DirectoryProperty

  /**
   * Temporary storage of individual publications.
   *
   * This value is intended for internal use and should not typically be configured in build scripts.
   */
  @DevPublishInternalApi
  abstract val publicationsStore: DirectoryProperty

  /**
   * Location of stored
   * [dev.adamko.gradle.dev_publish.data.PublicationData]
   * checksums used to determine if a publication task is up-to-date.
   *
   * This value is intended for internal use and should not typically be configured in build scripts.
   */
  @DevPublishInternalApi
  abstract val checksumsStore: DirectoryProperty
}
