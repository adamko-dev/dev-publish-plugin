package dev.adamko.gradle.dev_publish.tasks

import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__MAVEN_REPO_NAME
import dev.adamko.gradle.dev_publish.DevPublishPluginExtension
import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import dev.adamko.gradle.dev_publish.utils.registerDeprecatedTask
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

  /** Generates the checksums that are used for the publication up-to-date checks. */
  val generateDevPublishChecksums: TaskProvider<GeneratePublicationDataChecksumTask> =
    tasks.registerGenerateDevPublishChecksumsTask()

  val updateDevRepo: TaskProvider<UpdateDevRepoTask> =
    tasks.registerUpdateDevRepoTask()

  /** Writes the dev repo location into a file, for putting on a test runtime classpath. */
  val generateDevPublishMetadata: TaskProvider<GenerateDevPublishMetadataTask> =
    tasks.registerGenerateDevPublishMetadataTask()

  init {
    tasks.registerGenerateDevPublishChecksumsLegacyAlias()
  }

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

      // always auto-refresh stored checksums
      finalizedBy(generateDevPublishChecksums)
    }

  private fun TaskContainer.registerGenerateDevPublishChecksumsTask(): TaskProvider<GeneratePublicationDataChecksumTask> =
    register<GeneratePublicationDataChecksumTask>(GENERATE_DEV_PUBLISH_CHECKSUMS_TASK_NAME) {
      description = "Generates checksums from the Maven publications, used for up-to-date checks. " +
          "This is an internal task that should not typically be manually referenced or called."
      outputDirectory.convention(devPubExtension.checksumsStore)
      tempDir.convention(objects.directoryProperty().fileValue(temporaryDir))
    }

  private fun TaskContainer.registerGenerateDevPublishMetadataTask(): TaskProvider<GenerateDevPublishMetadataTask> =
    register<GenerateDevPublishMetadataTask>(GENERATE_DEV_PUBLISH_METADATA_TASK_NAME) {
      description = "Writes the dev Maven repository location into a properties file, " +
          "so it can be read from a test runtime classpath. " +
          "This is an internal task that should not typically be manually referenced or called."

      outputDirectory.convention(devPubExtension.devMavenRepoMetadataDir)
      devMavenRepo.convention(updateDevRepo.flatMap { it.devRepo })
      @Suppress("UnstableApiUsage")
      rootProjectDir.convention(project.isolated.rootProject.projectDirectory)
    }

  @Suppress("DEPRECATION")
  private fun TaskContainer.registerGenerateDevPublishChecksumsLegacyAlias(): TaskProvider<BaseDevPublishTask> =
    registerDeprecatedTask(
      deprecatedName = GENERATE_PUBLICATION_CHECKSUM_TASK_NAME_LEGACY,
      replacementName = generateDevPublishChecksums.name
    )

  private fun TaskContainer.registerUpdateDevRepoTask(): TaskProvider<UpdateDevRepoTask> =
    register<UpdateDevRepoTask>(UPDATE_DEV_REPO_TASK_NAME) {
      description = "Updates the dev-repo"
      publicationsStore.set(devPubExtension.publicationsStore)
      devRepo.set(devPubExtension.devMavenRepo)

      dependsOn(publishAllToDevRepo)

      // always auto-refresh stored checksums
      finalizedBy(generateDevPublishChecksums)
    }

  @DevPublishInternalApi
  companion object {
    const val PUBLISH_ALL_TO_DEV_REPO_TASK_NAME = "publishAllToDevRepo"
    const val UPDATE_DEV_REPO_TASK_NAME = "updateDevRepo"
    const val GENERATE_DEV_PUBLISH_METADATA_TASK_NAME = "generateDevPublishMetadata"
    const val GENERATE_DEV_PUBLISH_CHECKSUMS_TASK_NAME = "generateDevPublishChecksums"

    /** @see GENERATE_DEV_PUBLISH_CHECKSUMS_TASK_NAME */
    @Deprecated("Renamed to generateDevPublishChecksums. Scheduled for removal in version 2.0.0.")
    const val GENERATE_PUBLICATION_CHECKSUM_TASK_NAME_LEGACY = "generatePublicationHashTask"
  }
}
