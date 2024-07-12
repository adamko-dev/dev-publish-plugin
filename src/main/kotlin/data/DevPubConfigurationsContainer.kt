package dev.adamko.gradle.dev_publish.data

import dev.adamko.gradle.dev_publish.DevPublishPlugin
import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_DEPENDENCIES
import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_INCOMING
import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_OUTGOING
import dev.adamko.gradle.dev_publish.data.DevPubAttributes.Companion.DevPublishTypeAttribute
import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import dev.adamko.gradle.dev_publish.utils.consumable
import dev.adamko.gradle.dev_publish.utils.declarable
import dev.adamko.gradle.dev_publish.utils.get
import dev.adamko.gradle.dev_publish.utils.resolvable
import java.io.File
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.gradle.api.provider.Provider

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

  private val devPublicationApiDependencies: Configuration =
    configurations.create(DevPublishPlugin.DEV_PUB__PUBLICATION_API_DEPENDENCIES) {
      description =
        "Declare dependencies on test Maven Publications." +
            "The publications will also be shared with consumers of this subproject."
      declarable()
    }

  private val devPublicationDependencies: Configuration =
    configurations.create(DEV_PUB__PUBLICATION_DEPENDENCIES) {
      description = "Declare dependencies on test Maven Publications."
      declarable()
    }

  val testMavenPublicationResolver: Configuration =
    configurations.create(DEV_PUB__PUBLICATION_INCOMING) {
      description = "Resolve test Maven Publications."
      resolvable()
      attributes {
        attribute(USAGE_ATTRIBUTE, devPubAttributes.devPublishUsage)
        attribute(CATEGORY_ATTRIBUTE, devPubAttributes.devPublishCategory)
        attribute(DevPublishTypeAttribute, devPubAttributes.mavenRepositoryType)
      }
      extendsFrom(devPublicationApiDependencies)
      extendsFrom(devPublicationDependencies)
    }

  val testMavenPublicationApiElements: Configuration =
    configurations.create(DEV_PUB__PUBLICATION_OUTGOING) {
      description = "Provide test Maven Publications."
      consumable()
      attributes {
        attribute(USAGE_ATTRIBUTE, devPubAttributes.devPublishUsage)
        attribute(CATEGORY_ATTRIBUTE, devPubAttributes.devPublishCategory)
        attribute(DevPublishTypeAttribute, devPubAttributes.mavenRepositoryType)
      }
      extendsFrom(devPublicationApiDependencies)
    }

  fun resolvedDevRepos1(): Provider<List<File>> {
    return testMavenPublicationResolver
      .incoming
      .artifactView {
        attributes {
          attribute(USAGE_ATTRIBUTE, devPubAttributes.devPublishUsage)
          attribute(CATEGORY_ATTRIBUTE, devPubAttributes.devPublishCategory)
          attribute(DevPublishTypeAttribute, devPubAttributes.mavenRepositoryType)
        }
        lenient(true)
        @Suppress("UnstableApiUsage")
        withVariantReselection()
      }
      .artifacts
      .resolvedArtifacts
      .map { artifacts ->
        artifacts
          .filter {
            /**/ it.variant.attributes[USAGE_ATTRIBUTE] == devPubAttributes.devPublishUsage
              && it.variant.attributes[CATEGORY_ATTRIBUTE] == devPubAttributes.devPublishUsage
              && it.variant.attributes[DevPublishTypeAttribute] == devPubAttributes.mavenRepositoryType
          }
          .map(ResolvedArtifactResult::getFile)
      }
  }

//  fun resolvedDevRepos2() {
//    testMavenPublicationResolver
//      .incoming
//      .resolutionResult
//      .rootComponent
//      .map { component ->
//        component.variants
//          .filter {
//            /**/ it.attributes[USAGE_ATTRIBUTE] == devPubAttributes.devPublishUsage
//              && it.attributes[CATEGORY_ATTRIBUTE] == devPubAttributes.devPublishUsage
//              && it.attributes[DevPublishTypeAttribute] == devPubAttributes.mavenRepositoryType
//          }
//          .flatMap { component.getDependenciesForVariant(it) }
//          .map { it }
//
//        component
//      }
//  }

  @DevPublishInternalApi
  companion object
}
