package dev.adamko.gradle.dev_publish

import org.gradle.api.file.DirectoryProperty

/**
 * Settings for controlling the behaviour of [DevPublishPlugin] and tasks.
 */
abstract class DevPublishPluginExtension {
  /**
   * Location of the file-based test Maven repository.
   *
   * The repository is specific per subproject and should not be shared.
   *
   * This property should be passed into tests and provided as a Maven repository.
   */
  abstract val devMavenRepo: DirectoryProperty

  /**
   * Location of temporary Maven repository, that will be used to populate [devMavenRepo].
   *
   * This value should not typically be configured or used.
   */
  abstract val stagingDevMavenRepo: DirectoryProperty

  /**
   * Location of stored
   * [dev.adamko.gradle.dev_publish.data.PublicationData]
   * checksums used to determine if a publication task is up-to-date.
   *
   * This value should not typically be configured or used.
   */
  abstract val checksumsStore: DirectoryProperty
}
