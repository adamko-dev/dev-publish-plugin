package dev.adamko.gradle.dev_publish.services

import dev.adamko.gradle.dev_publish.data.PublicationData
import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import javax.inject.Inject
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.newInstance


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
    publication: MavenPublication?,
  ): PublicationData? {
    if (publication == null) {
      logger.warn("cannot create PublicationData - MavenPublication is null")
      return null
    }

    // TODO de-dupe
    val artifacts = providers.provider { publication.artifacts }
      .map { artifacts ->
        objects.fileCollection()
          .from(artifacts.map { it.file })
          .builtBy(artifacts)
      }
    val identifier = providers.provider { publication.run { "$groupId:$artifactId:$version" } }

    return objects.newInstance<PublicationData>(publication.name).apply {
      this.identifier.set(identifier)
      this.artifacts.from(artifacts)
    }
  }

  @DevPublishInternalApi
  companion object {
    private val logger = Logging.getLogger(DevPublishService::class.java)

    const val SERVICE_NAME = "DevPublishService"
  }
}
