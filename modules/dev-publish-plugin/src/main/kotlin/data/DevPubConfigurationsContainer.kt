@file:Suppress("UnstableApiUsage")

package dev.adamko.gradle.dev_publish.data

import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_API_DEPENDENCIES
import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_DEPENDENCIES
import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_INCOMING
import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_OUTGOING
import dev.adamko.gradle.dev_publish.data.DevPubAttributes.Companion.DevPublishTypeAttribute
import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import dev.adamko.gradle.dev_publish.utils.extendsFrom_
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE

/**
 * Utility class that contains all [Configuration]s used by [dev.adamko.gradle.dev_publish.DevPublishPlugin].
 */
@DevPublishInternalApi
class DevPubConfigurationsContainer(
  private val devPubAttributes: DevPubAttributes,
  dependencies: DependencyHandler,
  configurations: ConfigurationContainer,
) {

  init {
    dependencies.attributesSchema.attribute(DevPublishTypeAttribute)
  }

  private val devPublicationApiDependencies: NamedDomainObjectProvider<DependencyScopeConfiguration> =
    configurations.dependencyScope(DEV_PUB__PUBLICATION_API_DEPENDENCIES) {
      description = "Deprecated. Use `$DEV_PUB__PUBLICATION_DEPENDENCIES` instead."
    }

  private val devPublicationDependencies: NamedDomainObjectProvider<DependencyScopeConfiguration> =
    configurations.dependencyScope(DEV_PUB__PUBLICATION_DEPENDENCIES) {
      description = "Declare dependencies on test Maven Publications."
    }

  val devMavenPublicationResolver: NamedDomainObjectProvider<ResolvableConfiguration> =
    configurations.resolvable(DEV_PUB__PUBLICATION_INCOMING) {
      description = "Resolve dev Maven Publications."
      extendsFrom_(devPublicationApiDependencies)
      extendsFrom_(devPublicationDependencies)
      attributes {
        mavenRepositoryType()
      }
    }

  val devMavenPublicationApiElements: NamedDomainObjectProvider<ConsumableConfiguration> =
    configurations.consumable(DEV_PUB__PUBLICATION_OUTGOING) {
      description = "Provide dev Maven Publications."
      attributes {
        mavenRepositoryType()
      }
      extendsFrom_(devPublicationApiDependencies)
    }

  private fun AttributeContainer.mavenRepositoryType() {
    attribute(USAGE_ATTRIBUTE, devPubAttributes.devPublishUsage)
    attribute(CATEGORY_ATTRIBUTE, devPubAttributes.devPublishCategory)
    attribute(DevPublishTypeAttribute, devPubAttributes.mavenRepositoryType)
  }

  @DevPublishInternalApi
  companion object
}
