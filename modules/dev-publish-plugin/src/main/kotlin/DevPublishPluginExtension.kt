package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * Settings for controlling the behaviour of [DevPublishPlugin] and its tasks.
 */
abstract class DevPublishPluginExtension
@DevPublishInternalApi constructor() {

  /**
   * Declare this to make a source set depend on dev publishing.
   *
   * ```kotlin
   * dependencies {
   *   testImplementation(devPublish.dependency())
   * }
   * // or inside a JvmTestSuite
   * testing.suites {
   *   register<JvmTestSuite>("functionalTest") {
   *     dependencies {
   *       implementation(devPublish.dependency())
   *     }
   *   }
   * }
   * ```
   *
   * Declaring it runs `updateDevRepo` before the tests, puts the [devMavenRepo] location on their
   * runtime classpath, and adds `dev-publish-utils` for reading it.
   */
  fun dependency(): Provider<out Dependency> = dependency

  internal abstract val dependency: Property<Dependency>

  /**
   * Location of the file-based test Maven repository.
   *
   * The repository is specific per subproject and should not be referenced by other subprojects.
   *
   * This property should be passed into tests and provided as a Maven repository.
   * The simplest way to do that is to declare [dependency].
   *
   * Default: `build/maven-dev`
   *
   * @see dependency
   */
  abstract val devMavenRepo: DirectoryProperty

  /**
   * Directory holding the generated metadata file that records the [devMavenRepo] location.
   *
   * This value is intended for internal use and should not typically be configured in build scripts.
   */
  @DevPublishInternalApi
  abstract val devMavenRepoMetadataDir: DirectoryProperty
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
