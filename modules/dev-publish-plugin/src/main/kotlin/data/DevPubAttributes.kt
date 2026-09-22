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

  val mavenRepositoryType = "maven-repository"

  @DevPublishInternalApi
  companion object {
    val DevPublishTypeAttribute: Attribute<String> =
      Attribute("dev.adamko.gradle.dev_publish.type")

    /**
     * Indicates a [Configuration] contains the generated file recording the dev Maven repository
     * location, and the library that reads it.
     */
    const val DEV_REPO_METADATA_KEY = "dev-maven-repo-metadata"
  }
}
