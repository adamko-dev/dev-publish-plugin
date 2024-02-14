package dev.adamko.gradle.dev_publish.data

import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_API_DEPENDENCIES
import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_DEPENDENCIES
import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_INCOMING
import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_OUTGOING
import dev.adamko.gradle.dev_publish.data.DevPubAttributes.Companion.DevPublishTypeAttribute
import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import dev.adamko.gradle.dev_publish.utils.consumable
import dev.adamko.gradle.dev_publish.utils.declarable
import dev.adamko.gradle.dev_publish.utils.resolvable
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE

/**
 * Utility class that contains all [Configuration]s used by [dev.adamko.gradle.dev_publish.DevPublishPlugin].
 */
@DevPublishInternalApi
class DevPubConfigurationsContainer(
  devPubAttributes: DevPubAttributes,
  dependencies: DependencyHandler,
  configurations: ConfigurationContainer,
) {

  init {
    // register the attribute for consuming/providing
    dependencies.attributesSchema.attribute(DevPublishTypeAttribute)
  }

  private val devPublicationApiDependencies: NamedDomainObjectProvider<Configuration> =
    configurations.register(DEV_PUB__PUBLICATION_API_DEPENDENCIES) {
      description =
        "Declare dependencies on test Maven Publications, that will be propagated to consumers of this subproject"
      declarable()
    }

  private val devPublicationDependencies: NamedDomainObjectProvider<Configuration> =
    configurations.register(DEV_PUB__PUBLICATION_DEPENDENCIES) {
      description = "Declare dependencies on test Maven Publications"
      declarable()
      extendsFrom(devPublicationApiDependencies.get())
    }

  val testMavenPublicationResolver: NamedDomainObjectProvider<Configuration> =
    configurations.register(DEV_PUB__PUBLICATION_INCOMING) {
      description = "Resolve test Maven Publications"
      resolvable()
      attributes {
        attribute(USAGE_ATTRIBUTE, devPubAttributes.devPublishUsage)
        attribute(DevPublishTypeAttribute, devPubAttributes.mavenRepositoryType)
      }
      extendsFrom(devPublicationDependencies.get())
    }

  val testMavenPublicationApiElements: NamedDomainObjectProvider<Configuration> =
    configurations.register(DEV_PUB__PUBLICATION_OUTGOING) {
      description = "Provide test Maven Publications"
      consumable()
      attributes {
        attribute(USAGE_ATTRIBUTE, devPubAttributes.devPublishUsage)
        attribute(DevPublishTypeAttribute, devPubAttributes.mavenRepositoryType)
      }
      extendsFrom(devPublicationApiDependencies.get())
    }

  @DevPublishInternalApi
  companion object
}
