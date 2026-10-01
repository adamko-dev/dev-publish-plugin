package dev.adamko.gradle.dev_publish.internal

/**
 * The metadata file that the DevPublish Gradle plugin writes, and `dev-publish-utils` reads back
 * inside a test JVM.
 */
@DevPublishInternalApi
object DevPublishMetadata {

  /**
   * File containing the relative path to the dev-maven repo.
   *
   * The path is relative to the _parent directory_ of the metadata file.
   */
  const val REPO_LOCATION_FILE_NAME = "dev.publish.metadata.repo-location.txt"
}
