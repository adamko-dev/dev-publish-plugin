package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty

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
   * Patterns of files in [devMavenRepo] that are ignored when checking if tests that depend on it are up-to-date.
   * Exclude files that change on every build, but that tests don't use, such as SBOMs.
   * See [org.gradle.api.tasks.util.PatternFilterable] for syntax.
   *
   * Default: Maven metadata (`maven-metadata.xml`), checksums, and signatures.
   */
  abstract val excludedDevMavenRepoFilePatterns: SetProperty<String>

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
   * Temporary storage of this project's current publications, collected by `publishAllToDevRepo`.
   *
   * This value is intended for internal use and should not typically be configured in build scripts.
   */
  @DevPublishInternalApi
  abstract val publicationsStore: DirectoryProperty

  /** The repo written by each dev repo publish task. Added to by each task, when it is configured. */
  @DevPublishInternalApi
  abstract val publicationDirs: ConfigurableFileCollection

  /**
   * Location of stored
   * [dev.adamko.gradle.dev_publish.data.PublicationData]
   * checksums used to determine if a publication task is up-to-date.
   *
   * This value is intended for internal use and should not typically be configured in build scripts.
   */
  @DevPublishInternalApi
  @Deprecated("No longer used: Gradle's own up-to-date checks decide when to publish. Scheduled for removal in version 2.0.")
  abstract val checksumsStore: DirectoryProperty
}
