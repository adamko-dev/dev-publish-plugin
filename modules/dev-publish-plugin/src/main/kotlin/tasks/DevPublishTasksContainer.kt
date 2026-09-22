package dev.adamko.gradle.dev_publish.tasks

import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__MAVEN_REPO_NAME
import dev.adamko.gradle.dev_publish.DevPublishPluginExtension
import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import dev.adamko.gradle.dev_publish.internal.DevPublishProblemGroup
import org.gradle.api.model.ObjectFactory
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.Problems
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.support.serviceOf

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
  val generatePublicationChecksum: TaskProvider<GeneratePublicationDataChecksumTask> =
    tasks.registerGeneratePublicationChecksumTask()

  /** Collects all dev publications into the current subproject's dev repo. */
  val updateDevRepo: TaskProvider<UpdateDevRepoTask> =
    tasks.registerUpdateDevRepoTask()

  /** Writes the dev repo location into a file, for putting on a test runtime classpath. */
  val generateDevPublishMetadata: TaskProvider<GenerateDevPublishMetadataTask> =
    tasks.registerGenerateDevPublishMetadataTask()

  init {
    tasks.registerGeneratePublicationChecksumLegacyAlias()
  }

  private fun TaskContainer.registerPublishAllToDevRepoTask(): TaskProvider<BaseDevPublishTask> =
    register<BaseDevPublishTask>(PUBLISH_ALL_TO_DEV_REPO_TASK_NAME) {
      description = "Publishes all Maven publications from this project into the dev Maven " +
          "repository staging area. This is an internal task that should not typically be " +
          "manually referenced or called - depend on `$UPDATE_DEV_REPO_TASK_NAME` instead."

      outputs.dir(devPubExtension.stagingDevMavenRepo)

      dependsOn(
        // I would like to check using repository.name == DEV_PUB__MAVEN_REPO_NAME,
        // but the task's repo property is set lazily and doesn't have a nice
        // provider property, so checking via the task name will have to do:
        project.tasks.matching { it.name == "publishAllPublicationsTo${DEV_PUB__MAVEN_REPO_NAME}Repository" }
      )

      // always auto-refresh stored checksums
      finalizedBy(generatePublicationChecksum)
    }

  private fun TaskContainer.registerGeneratePublicationChecksumTask(): TaskProvider<GeneratePublicationDataChecksumTask> =
    register<GeneratePublicationDataChecksumTask>(GENERATE_PUBLICATION_CHECKSUM_TASK_NAME) {
      description = "Generates checksums from the Maven publications, used for up-to-date checks. " +
          "This is an internal task that should not typically be manually referenced or called."
      outputDirectory.convention(devPubExtension.checksumsStore)
      tempDir.convention(objects.directoryProperty().fileValue(temporaryDir))
    }

  @Suppress("DEPRECATION")
  private fun TaskContainer.registerGeneratePublicationChecksumLegacyAlias(): TaskProvider<BaseDevPublishTask> =
    register<BaseDevPublishTask>(GENERATE_PUBLICATION_CHECKSUM_TASK_NAME_LEGACY) {
      group = null // hide the deprecated task from `gradlew tasks`
      val replacementTask = generatePublicationChecksum.name

      description = "Deprecated. Use `$replacementTask` instead."

      dependsOn(generatePublicationChecksum)

      @Suppress("UnstableApiUsage")
      val problems = project.serviceOf<Problems>()
      @Suppress("UnstableApiUsage")
      doLast {
        problems.reporter.report(
          ProblemId.create("deprecated-task", "Deprecated task", DevPublishProblemGroup)
        ) {
          contextualLabel("Task '$path' is deprecated")
          details("Task '$name' was renamed to '$replacementTask'")
          solution("Use $replacementTask instead of '$name'.")
        }
      }
    }

  private fun TaskContainer.registerGenerateDevPublishMetadataTask(): TaskProvider<GenerateDevPublishMetadataTask> =
    register<GenerateDevPublishMetadataTask>(GENERATE_DEV_PUBLISH_METADATA_TASK_NAME) {
      description = "Writes the dev Maven repository location into a properties file, so it can " +
          "be read from a test runtime classpath. This is an internal task that should not " +
          "typically be manually referenced or called."

      outputDirectory.convention(devPubExtension.devMavenRepoMetadataDir)
      devMavenRepo.convention(updateDevRepo.flatMap { it.devRepo })
    }

  private fun TaskContainer.registerUpdateDevRepoTask(): TaskProvider<UpdateDevRepoTask> =
    register<UpdateDevRepoTask>(UPDATE_DEV_REPO_TASK_NAME) {
      description = "Publishes all Maven publications from this project, and from every " +
          "project declared as a `devPublication` dependency, into the dev Maven repository " +
          "(devPublish.devMavenRepo). Test tasks should depend on this task."
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
    const val GENERATE_DEV_PUBLISH_METADATA_TASK_NAME = "generateDevPublishMetadata"

    const val GENERATE_PUBLICATION_CHECKSUM_TASK_NAME = "generatePublicationChecksums"

    /** @see GENERATE_PUBLICATION_CHECKSUM_TASK_NAME */
    @Deprecated("Renamed to generatePublicationChecksums. Scheduled for removal in version 2.0.0.")
    const val GENERATE_PUBLICATION_CHECKSUM_TASK_NAME_LEGACY = "generatePublicationHashTask"
  }
}
