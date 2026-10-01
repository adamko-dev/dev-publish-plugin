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
  /** Indicates a [Configuration] contains a Maven Repository */
  val devPublishUsage: Usage = objects.named("dev-publish")

  /** Indicates a [Configuration] contains a Maven Repository */
  val devPublishCategory: Category = objects.named("dev-publish")

  val mavenRepositoryType = "maven-repository"

  @DevPublishInternalApi
  companion object {
    val DevPublishTypeAttribute: Attribute<String> =
      Attribute("dev.adamko.gradle.dev_publish.type")
  }
}
