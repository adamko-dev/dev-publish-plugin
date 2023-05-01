package dev.adamko.gradle.dev_publish.data

import dev.adamko.gradle.dev_publish.DevPublishPlugin
import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_PROVIDED_DEPENDENCIES
import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import dev.adamko.gradle.dev_publish.utils.asConsumer
import dev.adamko.gradle.dev_publish.utils.asProvider
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject

/**
 * Utility class that contains all [Configuration]s used by [dev.adamko.gradle.dev_publish.DevPublishPlugin].
 */
@DevPublishInternalApi
abstract class DevPubConfigurationsContainer @Inject constructor(
  private val dependencies: DependencyHandler,
  configurations: ConfigurationContainer,
  objects: ObjectFactory,
) {

  private val devPubAttributes: Attributes = objects.createConfigurationAttributes()

  val testMavenPublicationConsumer = configurations.registerPublicationsConsumer()
  val testMavenPublicationProvider = configurations.registerPublicationsProvider()

  private fun ObjectFactory.createConfigurationAttributes(): Attributes {
    // register the attribute for consuming/providing
    dependencies.attributesSchema.attribute(Attributes.DEV_PUB_USAGE)

    return newInstance<Attributes>()
  }

  private fun ConfigurationContainer.registerPublicationsConsumer(): NamedDomainObjectProvider<Configuration> {
    return register(DevPublishPlugin.DEV_PUB__PUBLICATION_INCOMING_DEPENDENCIES) {
      asConsumer()
      attributes { attribute(Attributes.DEV_PUB_USAGE, devPubAttributes.mavenRepoUsage) }
    }
  }

  private fun ConfigurationContainer.registerPublicationsProvider(): NamedDomainObjectProvider<Configuration> {
    return register(DEV_PUB__PUBLICATION_PROVIDED_DEPENDENCIES) {
      description = "Provide test Maven Publications to other subprojects"
      asProvider()
      extendsFrom(testMavenPublicationConsumer.get())
      attributes { attribute(Attributes.DEV_PUB_USAGE, devPubAttributes.mavenRepoUsage) }
    }
  }

  /**
   * Gradle [Configuration] Attributes for sharing files across subprojects.
   *
   * These attributes are used to tag [Configuration]s, so files can be shared between subprojects.
   */
  @DevPublishInternalApi
  abstract class Attributes
  @Inject
  constructor(
    objects: ObjectFactory,
  ) {

    /** Indicates a [Configuration] contains a Maven Repository */
    val mavenRepoUsage: MavenPublishTestUsage = objects.named("maven-repository")

    @DevPublishInternalApi
    interface MavenPublishTestUsage : Usage

    @DevPublishInternalApi
    companion object {
      val DEV_PUB_USAGE = Attribute<MavenPublishTestUsage>("dev.adamko.gradle.dev_publish.usage")

      private inline fun <reified T> Attribute(name: String): Attribute<T> =
        Attribute.of(name, T::class.java)
    }
  }

  @DevPublishInternalApi
  companion object {
    internal fun ObjectFactory.newDevPubConfigurationsContainer(
      dependencies: DependencyHandler,
      configurations: ConfigurationContainer,
      objects: ObjectFactory,
    ): DevPubConfigurationsContainer {
      return newInstance<DevPubConfigurationsContainer>(
        dependencies,
        configurations,
        objects,
      )
    }
  }
}
