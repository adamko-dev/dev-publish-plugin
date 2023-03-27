package dev.adamko.gradle.dev_publish.internal


import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.named
import javax.inject.Inject


/**
 * Gradle Configuration Attributes for sharing files across subprojects.
 *
 * These attributes are used to tag [Configuration]s, so files can be shared between subprojects.
 */
@DevPublishInternalApi
abstract class DevPublishConfigurationAttributes
@Inject
constructor(
  objects: ObjectFactory,
) {

  /** Indicates a [Configuration] concerts a Maven Repository */
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
