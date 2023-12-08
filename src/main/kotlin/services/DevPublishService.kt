package dev.adamko.gradle.dev_publish.services

import dev.adamko.gradle.dev_publish.data.PublicationData
import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject


/**
 * Utility service for managing [dev.adamko.gradle.dev_publish.DevPublishPlugin] operations.
 *
 * This service is primarily used to ensure that [org.gradle.api.publish.maven.tasks.PublishToMavenRepository] tasks
 * do not run concurrently (via [org.gradle.api.services.BuildServiceSpec.getMaxParallelUsages]), allowing for published
 * files to be accurately captured and synced to the maven-dev repository.
 */
@DevPublishInternalApi
abstract class DevPublishService @Inject constructor(
  private val providers: ProviderFactory,
  private val objects: ObjectFactory,
) : BuildService<DevPublishService.Parameters> {

  @DevPublishInternalApi
  interface Parameters : BuildServiceParameters

  /** Create an instance of [PublicationData] from [publication]. */
  fun createPublicationData(
    publication: MavenPublication?
  ): PublicationData? {
    if (publication == null) return null

    return objects.newInstance<PublicationData>(publication.name).apply {
      identifier.set(
        providers.provider {
          "${publication.groupId}:${publication.artifactId}:${publication.version}"
        }
      )
      artifacts.from(providers.provider {
        publication.artifacts.map { it.file }
      })
    }
  }

  @DevPublishInternalApi
  companion object {
    const val SERVICE_NAME = "DevPublishManifest"
  }
}
