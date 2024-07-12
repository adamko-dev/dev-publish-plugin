package dev.adamko.gradle.dev_publish.tasks

import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__MAVEN_REPO_NAME
import dev.adamko.gradle.dev_publish.DevPublishPluginExtension
import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

/** Container for all [dev.adamko.gradle.dev_publish.DevPublishPlugin] tasks. */
@DevPublishInternalApi
class DevPublishTasksContainer(
  tasks: TaskContainer,
  private val devPubExtension: DevPublishPluginExtension,
  private val objects: ObjectFactory,
) {

  /** Lifecycle task for publishing dev repos to the current subproject's dev repo. */
  val publishAllToDevRepo: TaskProvider<BaseDevPublishTask> =
    tasks.registerPublishAllToDevRepoTask()

  /** Lifecycle task for publishing dev repos to the current subproject's dev repo. */
  val generatePublicationChecksum: TaskProvider<GeneratePublicationDataChecksumTask> =
    tasks.registerGeneratePublicationChecksumTask()

  val updateDevRepo: TaskProvider<UpdateDevRepoTask> =
    tasks.registerUpdateDevRepoTask()

  private fun TaskContainer.registerPublishAllToDevRepoTask(): TaskProvider<BaseDevPublishTask> =
    register<BaseDevPublishTask>(PUBLISH_ALL_TO_DEV_REPO_TASK_NAME) {
      description = "Publishes all Maven publications to the dev Maven repository. " +
          "This is an internal task that should not typically be manually referenced or called."

      outputs.dir(devPubExtension.stagingDevMavenRepo)

      dependsOn(
        // I would like to check using repository.name == DEV_PUB__MAVEN_REPO_NAME,
        // but the task's repo property is set lazily and doesn't have a nice
        // provider property, so checking via the task name will have to do:
        project.tasks.matching { it.name == "publishAllPublicationsTo${DEV_PUB__MAVEN_REPO_NAME}Repository" }
      )
    }

  private fun TaskContainer.registerGeneratePublicationChecksumTask(): TaskProvider<GeneratePublicationDataChecksumTask> =
    register<GeneratePublicationDataChecksumTask>(GENERATE_PUBLICATION_CHECKSUM_TASK) {
      description = "Generates a checksum from a publication, used for up-to-date checks. " +
          "This is an internal task that should not typically be manually referenced or called."
      outputDirectory.convention(devPubExtension.checksumsStore)
      tempDir.convention(objects.directoryProperty().fileValue(temporaryDir))
    }

  private fun TaskContainer.registerUpdateDevRepoTask(): TaskProvider<UpdateDevRepoTask> =
    register<UpdateDevRepoTask>(UPDATE_DEV_REPO_TASK_NAME) {
      description = "Updates the dev-repo"
      publicationsStore.set(devPubExtension.publicationsStore)
      devRepo.set(devPubExtension.devMavenRepo)

      dependsOn(publishAllToDevRepo)

      // always auto-refresh stored checksums
      finalizedBy(generatePublicationChecksum)
    }

  @DevPublishInternalApi
  companion object {
    const val PUBLISH_ALL_TO_DEV_REPO_TASK_NAME = "publishAllToDevRepo"
    const val UPDATE_DEV_REPO_TASK_NAME = "updateDevRepo"
    const val GENERATE_PUBLICATION_CHECKSUM_TASK = "generatePublicationHashTask"
  }
}
