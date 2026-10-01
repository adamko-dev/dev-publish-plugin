@file:Suppress("UnstableApiUsage")

package dev.adamko.gradle.dev_publish.data

import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__ELEMENTS_CONFIGURATION
import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_API_DEPENDENCIES
import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_DEPENDENCIES
import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_INCOMING
import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_OUTGOING
import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__UTILS_CONFIGURATION
import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__UTILS_GROUP
import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__UTILS_MODULE
import dev.adamko.gradle.dev_publish.data.DevPubAttributes.Companion.DevPublishTypeAttribute
import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import dev.adamko.gradle.dev_publish.internal.DevPublishVersion
import dev.adamko.gradle.dev_publish.utils.deprecate
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
@Suppress("UnstableApiUsage")
@DevPublishInternalApi
class DevPubConfigurationsContainer(
  private val devPubAttributes: DevPubAttributes,
  dependencies: DependencyHandler,
  configurations: ConfigurationContainer,
) {

  init {
    dependencies.attributesSchema.attribute(DevPublishTypeAttribute)
  }

  @Deprecated("Renamed to $DEV_PUB__PUBLICATION_DEPENDENCIES")
  private val devPublicationApiDependencies: NamedDomainObjectProvider<DependencyScopeConfiguration> =
    configurations.dependencyScope(@Suppress("DEPRECATION") DEV_PUB__PUBLICATION_API_DEPENDENCIES) {
      description = "Deprecated. Use `$DEV_PUB__PUBLICATION_DEPENDENCIES` instead."
      deprecate(DEV_PUB__PUBLICATION_DEPENDENCIES)
    }

  val devPublicationDependencies: NamedDomainObjectProvider<DependencyScopeConfiguration> =
    configurations.dependencyScope(DEV_PUB__PUBLICATION_DEPENDENCIES) {
      description =
        "Declare dependencies on test Maven Publications. " +
            "The publications are also shared with consumers of this subproject."
      extendsFrom_(@Suppress("DEPRECATION") devPublicationApiDependencies)
    }

  val devMavenPublicationResolver: NamedDomainObjectProvider<ResolvableConfiguration> =
    configurations.resolvable(DEV_PUB__PUBLICATION_INCOMING) {
      description = "Resolve dev Maven Publications."
      extendsFrom_(devPublicationDependencies)
      attributes {
        mavenRepositoryType()
      }
    }

  val devMavenPublicationElements: NamedDomainObjectProvider<ConsumableConfiguration> =
    configurations.consumable(DEV_PUB__PUBLICATION_OUTGOING) {
      description = "Provide dev Maven Publications."
      attributes {
        mavenRepositoryType()
      }
      extendsFrom_(devPublicationDependencies)
    }

  private fun AttributeContainer.mavenRepositoryType() {
    attribute(USAGE_ATTRIBUTE, devPubAttributes.devPublishUsage)
    attribute(CATEGORY_ATTRIBUTE, devPubAttributes.devPublishCategory)
    attribute(DevPublishTypeAttribute, devPubAttributes.mavenRepositoryType)
  }

  val devPublishUtils: NamedDomainObjectProvider<DependencyScopeConfiguration> =
    configurations.dependencyScope(DEV_PUB__UTILS_CONFIGURATION) {
      description = "Declares the DevPublish helper library, for reading the dev Maven repo."
      defaultDependencies {
        add(dependencies.create("$DEV_PUB__UTILS_GROUP:$DEV_PUB__UTILS_MODULE:$DevPublishVersion"))
      }
    }

  val devPublishElements: NamedDomainObjectProvider<ConsumableConfiguration> =
    configurations.consumable(DEV_PUB__ELEMENTS_CONFIGURATION) {
      description = "The dev Maven repository location, and the library that reads it."

      extendsFrom_(devPublishUtils)

      attributes {
        // An attribute is required to trigger 'variant aware matching'.
        attribute(DevPublishTypeAttribute, DevPubAttributes.DEV_REPO_METADATA_KEY)
      }
    }

  @DevPublishInternalApi
  companion object
}
