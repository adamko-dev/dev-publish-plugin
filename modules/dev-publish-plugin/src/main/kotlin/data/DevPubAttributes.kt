package dev.adamko.gradle.dev_publish.data

import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import dev.adamko.gradle.dev_publish.utils.Attribute
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.named

/**
 * Gradle [Configuration] Attributes for sharing files across subprojects.
 *
 * These attributes are used to tag [Configuration]s, so contained files can be differentiated.
 */
@DevPublishInternalApi
class DevPubAttributes(
  objects: ObjectFactory,
) {
  /** Indicates a [Configuration] contains a Maven repository. */
  val devPublishUsage: Usage = objects.named("dev-publish")

  /** @see devPublishUsage */
  val devPublishCategory: Category = objects.named("dev-publish")

  val javaApiUsage: Usage = objects.named(Usage.JAVA_API)
  val javaRuntimeUsage: Usage = objects.named(Usage.JAVA_RUNTIME)
  val libraryCategory: Category = objects.named(Category.LIBRARY)

  val mavenRepositoryType = "maven-repository"
  val mavenRepositoryChecksumsType = "maven-repository-checksums"

  @DevPublishInternalApi
  companion object {
    val DevPublishTypeAttribute: Attribute<String> =
      Attribute("dev.adamko.gradle.dev_publish.type")
    /**
     * Indicates a [Configuration] provides the library that reads the dev Maven repository location,
     * and, for runtime, the generated file recording it.
     */
    const val DEV_REPO_METADATA_KEY = "dev-maven-repo-metadata"
  }
}
