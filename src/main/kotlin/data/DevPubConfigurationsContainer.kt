package dev.adamko.gradle.dev_publish.data

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
    // register the attribute for consuming/providing
    dependencies.attributesSchema.attribute(DevPublishTypeAttribute)
  }

  private val testMavenPublicationDependencies = configurations.registerPublicationsDependencies()
  val testMavenPublicationConsumer = configurations.registerPublicationsConsumer()
  val testMavenPublicationProvider = configurations.registerPublicationsProvider()

  private fun ConfigurationContainer.registerPublicationsDependencies(): NamedDomainObjectProvider<Configuration> =
    register(DEV_PUB__PUBLICATION_DEPENDENCIES) {
      description = "Declare dependencies on test Maven Publications"
      declarable()
    }

  private fun ConfigurationContainer.registerPublicationsConsumer(): NamedDomainObjectProvider<Configuration> =
    register(DEV_PUB__PUBLICATION_INCOMING) {
      description = "Resolve test Maven Publications"
      resolvable()
      attributes {
        attribute(USAGE_ATTRIBUTE, devPubAttributes.devPublishUsage)
        attribute(CATEGORY_ATTRIBUTE, devPubAttributes.devPublishCategory)
        attribute(DevPublishTypeAttribute, devPubAttributes.mavenRepositoryType)
      }
      extendsFrom(testMavenPublicationDependencies.get())
    }

  private fun ConfigurationContainer.registerPublicationsProvider(): NamedDomainObjectProvider<Configuration> =
    register(DEV_PUB__PUBLICATION_OUTGOING) {
      description = "Provide test Maven Publications"
      consumable()
      attributes {
        attribute(USAGE_ATTRIBUTE, devPubAttributes.devPublishUsage)
        attribute(CATEGORY_ATTRIBUTE, devPubAttributes.devPublishCategory)
        attribute(DevPublishTypeAttribute, devPubAttributes.mavenRepositoryType)
      }
      extendsFrom(testMavenPublicationDependencies.get())
    }

  @DevPublishInternalApi
  companion object
}
