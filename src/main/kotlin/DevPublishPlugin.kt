package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.data.PublicationData
import dev.adamko.gradle.dev_publish.internal.DevPublishConfigurationAttributes
import dev.adamko.gradle.dev_publish.internal.DevPublishConfigurationAttributes.Companion.DEV_PUB_USAGE
import dev.adamko.gradle.dev_publish.tasks.GeneratePublicationHashTask
import dev.adamko.gradle.dev_publish.tasks.UpdateDevRepoTask
import dev.adamko.gradle.dev_publish.utils.asConsumer
import dev.adamko.gradle.dev_publish.utils.asProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import java.io.File
import javax.inject.Inject
import kotlin.math.max

/**
 * Utility plugin for publishing subprojects to a local file-based Maven repository.
 *
 * The file-based repo (the location can be obtained from [DevPublishPluginExtension.devMavenRepo]) can be used in
 * functional tests, for example when using
 * [Gradle TestKit](https://docs.gradle.org/current/userguide/test_kit.html).
 *
 * This is useful for testing, as Maven metadata and Plugin Marker artifact gets published correctly.
 */
class DevPublishPlugin @Inject constructor(
  private val providers: ProviderFactory,
  private val layout: ProjectLayout,
  private val files: FileSystemOperations,
  private val objects: ObjectFactory,
) : Plugin<Project> {


  override fun apply(project: Project) {
    val devPubExtension = createExtension(project)

    val publishAllToDevRepoTask = createPublishAllToDevRepoTask(project, devPubExtension)
    val generatePublicationHashTask = createGeneratePublicationHashTask(project, devPubExtension)
    val updateDevRepoTask = createUpdateDevRepoTask(project, devPubExtension)

    updateDevRepoTask.configure {
      dependsOn(publishAllToDevRepoTask)

      // always auto-refresh stored checksums
      finalizedBy(generatePublicationHashTask)
    }

    project.plugins.withType<LifecycleBasePlugin>().configureEach {
      project.tasks.named(CHECK_TASK_NAME).configure {
        mustRunAfter(publishAllToDevRepoTask)
        mustRunAfter(generatePublicationHashTask)
        mustRunAfter(updateDevRepoTask)
      }
    }

    val configurationAttributes = objects.newInstance<DevPublishConfigurationAttributes>()
    // register the attribute for consuming/providing
    project.dependencies.attributesSchema.attribute(DEV_PUB_USAGE)

    val testMavenPublication = project.configurations.register(DEV_PUB__PUBLICATION_INCOMING_DEPENDENCIES) {
      asConsumer()
      attributes { attribute(DEV_PUB_USAGE, configurationAttributes.mavenRepoUsage) }
    }

    updateDevRepoTask.configure {
      // update this project's maven-test-repo with files from other subprojects
      from(testMavenPublication.map { conf ->
        conf.incoming.artifacts.artifactFiles
      })
    }

    project.configurations.register(DEV_PUB__PUBLICATION_PROVIDED_DEPENDENCIES) {
      description = "Provide test Maven Publications to other subprojects"
      asProvider()
      extendsFrom(testMavenPublication.get())
      attributes { attribute(DEV_PUB_USAGE, configurationAttributes.mavenRepoUsage) }
      outgoing {
        artifact(devPubExtension.devMavenRepo) {
          builtBy(updateDevRepoTask)
        }
      }
    }

    configureMavenPublishingPlugin(
      project,
      devPubExtension,
      generatePublicationHashTask,
    )

    project.tasks.withType<PublishToMavenRepository>().configureEach {

      // need to determine the repo lazily because the repo isn't set immediately
      val repoIsDevPub = providers.provider { repository?.name == DEV_PUB__MAVEN_REPO_NAME }.orElse(false)
      inputs.property("repoIsDevPub", repoIsDevPub)


      inputs
        // must convert to FileTree, because the directory might not exist, and
        // Gradle won't accept directories that don't exist as inputs.
        .files(devPubExtension.checksumsStore.asFileTree)
        .withPropertyName("devPubChecksumsStoreFiles")

      val publicationData = providers.provider {
        createPublicationData(publication)
      }
      val currentChecksum: Provider<String> = publicationData.map { data ->
        data.createChecksumContent()
      }

      val storedChecksum: Provider<String> = providers.zip(
        publicationData,
        devPubExtension.checksumsStore,
      ) { data, checksumStore ->
        checksumStore.asFile
          .resolve(data?.checksumFilename ?: "unknown")
          .takeIf(File::exists)
          ?.readText()
      }

      onlyIf("current checksums don't match stored checksum") {

        if (!repoIsDevPub.get()) return@onlyIf true

        logger.info(checksumsToDebugString(currentChecksum, storedChecksum))

        currentChecksum.orNull != storedChecksum.orNull
      }
    }
  }

  private fun createExtension(project: Project): DevPublishPluginExtension {
    return project.extensions.create<DevPublishPluginExtension>(DEV_PUB__EXTENSION_NAME).apply {
      devMavenRepo.convention(layout.buildDirectory.dir(DEV_PUB__MAVEN_REPO_DIR))
      stagingDevMavenRepo.convention(layout.buildDirectory.dir("tmp/.$DEV_PUB__MAVEN_REPO_DIR/staging"))
      checksumsStore.convention(layout.buildDirectory.dir("tmp/.$DEV_PUB__MAVEN_REPO_DIR/checksum-store"))
    }
  }

  private fun createPublishAllToDevRepoTask(
    project: Project,
    devPubExtension: DevPublishPluginExtension,
  ): TaskProvider<Task> =
    project.tasks.register("publishAllToDevRepo") {
      group = DEV_PUB__TASK_GROUP
      description = "Publishes all Maven publications to the dev Maven repository"

      outputs.dir(devPubExtension.stagingDevMavenRepo)

      dependsOn(
        // I would like to check using repository.name == DEV_PUB__MAVEN_REPO_NAME, but the task's
        // repo property is set lazily and doesn't have a nice provider property,
        // so this will have to do:
        project.tasks.matching { it.name == "publishAllPublicationsTo${DEV_PUB__MAVEN_REPO_NAME}Repository" }
      )
    }

  private fun createGeneratePublicationHashTask(
    project: Project,
    devPubExtension: DevPublishPluginExtension
  ): TaskProvider<GeneratePublicationHashTask> =
    project.tasks.register<GeneratePublicationHashTask>("generatePublicationHashTask") {
      outputDirectory.convention(devPubExtension.checksumsStore)
      tempDir.convention(objects.directoryProperty().fileValue(temporaryDir))
    }

  private fun createUpdateDevRepoTask(
    project: Project,
    devPubExtension: DevPublishPluginExtension,
  ): TaskProvider<UpdateDevRepoTask> =
    project.tasks.register<UpdateDevRepoTask>(DEV_PUB__UPDATE_DEV_REPO_TASK_NAME) {
      group = DEV_PUB__TASK_GROUP
      stagingRepo.set(devPubExtension.stagingDevMavenRepo)
      devRepo.set(devPubExtension.devMavenRepo)
    }

  private fun configureMavenPublishingPlugin(
    project: Project,
    devPubExtension: DevPublishPluginExtension,
    generatePublicationHashTask: TaskProvider<GeneratePublicationHashTask>,
  ) {
    project.plugins.withType<MavenPublishPlugin>().configureEach {
      project.extensions.configure<PublishingExtension> {
        repositories.maven(devPubExtension.stagingDevMavenRepo) {
          name = DEV_PUB__MAVEN_REPO_NAME
        }

        generatePublicationHashTask.configure {
          publicationData.addAllLater(providers.provider {
            publications
              .withType<MavenPublication>()
              .mapNotNull { createPublicationData(it) }
          })
        }
      }
    }
  }


  private fun createPublicationData(
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

  companion object {
    const val DEV_PUB__EXTENSION_NAME = "devPublish"
    const val DEV_PUB__TASK_GROUP = "dev publish"
    const val DEV_PUB__UPDATE_DEV_REPO_TASK_NAME = "updateDevRepo"

    /**
     * Name of the [org.gradle.api.artifacts.repositories.MavenArtifactRepository]
     * used for publishing test publications.
     */
    const val DEV_PUB__MAVEN_REPO_NAME = "DevPublishMaven"

    const val DEV_PUB__MAVEN_REPO_DIR = "maven-dev"

    const val DEV_PUB__PUBLICATION_INCOMING_DEPENDENCIES = "devPublication"
    const val DEV_PUB__PUBLICATION_PROVIDED_DEPENDENCIES = "devPublicationElements"

    private fun String.splitToPair(delimiter: String) =
      substringBefore(delimiter) to substringAfter(delimiter, "")

    /** Debug string of the [currentChecksum] and [storedChecksum] side-by-side */
    private fun checksumsToDebugString(
      currentChecksum: Provider<String>,
      storedChecksum: Provider<String>,
    ): String {
      fun Provider<String>.fileChecksumsMap() =
        getOrElse("")
          .lines()
          .filter { "=" in it }
          .map { it.splitToPair("=") }
          .groupBy({ it.first }, { it.second })

      val currentFileChecksums = currentChecksum.fileChecksumsMap()
      val storedFileChecksums = storedChecksum.fileChecksumsMap()

      val files = currentFileChecksums.keys + storedFileChecksums.keys

      val checksums = files.joinToString("\n") { file ->
        val currentChecksums = currentFileChecksums[file] ?: emptyList()
        val storedChecksums = storedFileChecksums[file] ?: emptyList()

        fun List<String>.getChecksum(i: Int) =
          getOrElse(i) { "<missing>" }.trim().padStart(40, ' ')

        val joinedChecksums = (0 until max(currentChecksums.size, storedChecksums.size))
          .joinToString("\n") { i ->
            val current = currentChecksums.getChecksum(i)
            val stored = storedChecksums.getChecksum(i)
            val matches = if (current == stored) "✅" else "❌"
            "  $current   $stored   $matches"
          }

        "$file\n$joinedChecksums"
      }

      return "current vs stored\n$checksums"
    }
  }
}
