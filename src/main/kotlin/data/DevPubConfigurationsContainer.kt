package dev.adamko.gradle.dev_publish.data

import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_DEPENDENCIES
import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_INCOMING
import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_OUTGOING
import dev.adamko.gradle.dev_publish.data.DevPubConfigurationsContainer.Attributes.Companion.DEV_PUB_USAGE
import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import dev.adamko.gradle.dev_publish.utils.consumable
import dev.adamko.gradle.dev_publish.utils.declarable
import dev.adamko.gradle.dev_publish.utils.resolvable
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

  private val testMavenPublicationDependencies = configurations.registerPublicationsDependencies()
  val testMavenPublicationConsumer = configurations.registerPublicationsConsumer()
  val testMavenPublicationProvider = configurations.registerPublicationsProvider()

  private fun ObjectFactory.createConfigurationAttributes(): Attributes {
    // register the attribute for consuming/providing
    dependencies.attributesSchema.attribute(DEV_PUB_USAGE)
    return newInstance<Attributes>()
  }

  private fun ConfigurationContainer.registerPublicationsDependencies(): NamedDomainObjectProvider<Configuration> =
    register(DEV_PUB__PUBLICATION_DEPENDENCIES) {
      description = "Declare dependencies on test Maven Publications"
      declarable()
    }

  private fun ConfigurationContainer.registerPublicationsConsumer(): NamedDomainObjectProvider<Configuration> =
    register(DEV_PUB__PUBLICATION_INCOMING) {
      description = "Resolve test Maven Publications"
      resolvable()
      attributes { attribute(DEV_PUB_USAGE, devPubAttributes.mavenRepoUsage) }
      extendsFrom(testMavenPublicationDependencies.get())
    }

  private fun ConfigurationContainer.registerPublicationsProvider(): NamedDomainObjectProvider<Configuration> =
    register(DEV_PUB__PUBLICATION_OUTGOING) {
      description = "Provide test Maven Publications"
      consumable()
      attributes { attribute(DEV_PUB_USAGE, devPubAttributes.mavenRepoUsage) }
      extendsFrom(testMavenPublicationDependencies.get())
    }

  /**
   * Gradle [Configuration] Attributes for sharing files across subprojects.
   *
   * These attributes are used to tag [Configuration]s, so files can be shared between subprojects.
   */
  @DevPublishInternalApi
  abstract class Attributes
  @Inject constructor(
    objects: ObjectFactory,
  ) {
    /** Indicates a [Configuration] contains a Maven Repository */
    val mavenRepoUsage: MavenPublishTestUsage = objects.named("maven-repository")

    @DevPublishInternalApi
    interface MavenPublishTestUsage : Usage

    @DevPublishInternalApi
    companion object {
      val DEV_PUB_USAGE = Attribute<MavenPublishTestUsage>("dev.adamko.gradle.dev_publish.usage")

      /** Instantiate a new [Attribute] of type [T] */
      private inline fun <reified T> Attribute(name: String): Attribute<T> = Attribute.of(name, T::class.java)
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
