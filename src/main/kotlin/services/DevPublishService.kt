package dev.adamko.gradle.dev_publish.services

import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import javax.inject.Inject
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters


/**
 * Utility service for managing [dev.adamko.gradle.dev_publish.DevPublishPlugin] operations.
 *
 * This service is primarily used to ensure that [org.gradle.api.publish.maven.tasks.PublishToMavenRepository] tasks
 * do not run concurrently (via [org.gradle.api.services.BuildServiceSpec.getMaxParallelUsages]), allowing for published
 * files to be accurately captured and synced to the maven-dev repository.
 */
@DevPublishInternalApi
abstract class DevPublishService
@Inject
constructor() : BuildService<DevPublishService.Parameters> {

  @DevPublishInternalApi
  interface Parameters : BuildServiceParameters

  @DevPublishInternalApi
  companion object {
    const val SERVICE_NAME = "DevPublishService"
  }
}
