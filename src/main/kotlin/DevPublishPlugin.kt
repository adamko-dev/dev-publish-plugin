package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.data.DevPubConfigurationsContainer.Companion.newDevPubConfigurationsContainer
import dev.adamko.gradle.dev_publish.services.DevPublishService
import dev.adamko.gradle.dev_publish.services.DevPublishService.Companion.SERVICE_NAME
import dev.adamko.gradle.dev_publish.tasks.DevPublishTasksContainer
import dev.adamko.gradle.dev_publish.utils.checksumsToDebugString
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import java.io.File
import javax.inject.Inject

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
  private val fs: FileSystemOperations,
  private val objects: ObjectFactory,
) : Plugin<Project> {

  override fun apply(project: Project) {
    val devPubExtension = project.extensions.createDevPublishExtension()

    val devPubService = project.gradle.sharedServices.registerDevPubService()

    val devPubTasks: DevPublishTasksContainer = objects.newInstance(
      project.tasks,
      devPubExtension,
    )

    val devPubConfigurations = objects.newDevPubConfigurationsContainer(
      dependencies = project.dependencies,
      configurations = project.configurations,
      objects = objects,
    )

    devPubTasks.updateDevRepo.configure {
      // update this project's maven-test-repo with files from other subprojects
      from(devPubConfigurations.testMavenPublicationConsumer.map { conf ->
        conf.incoming.artifacts.artifactFiles
      })
    }

    devPubConfigurations.testMavenPublicationProvider.configure {
      outgoing {
        artifact(devPubExtension.devMavenRepo) {
          builtBy(devPubTasks.updateDevRepo)
        }
      }
    }

    configureMavenPublishingPlugin(
      project = project,
      devPubExtension = devPubExtension,
      devPubTasks = devPubTasks,
      devPublishService = devPubService,
    )

    configureBasePlugin(
      project = project,
      devPubTasks = devPubTasks,
    )

    project.tasks.withType<PublishToMavenRepository>().configureEach {
      configurePublishToMavenRepositoryTask(devPubExtension, devPubService)
    }
  }

  private fun ExtensionContainer.createDevPublishExtension(): DevPublishPluginExtension {
    return create<DevPublishPluginExtension>(DEV_PUB__EXTENSION_NAME).apply {
      devMavenRepo.convention(layout.buildDirectory.dir(DEV_PUB__MAVEN_REPO_DIR))

      val tmpDir = layout.buildDirectory.dir("tmp/.$DEV_PUB__MAVEN_REPO_DIR/")

      stagingDevMavenRepo.convention(tmpDir.map { it.dir("staging") })
      checksumsStore.convention(tmpDir.map { it.dir("checksum-store") })
      publicationsStore.convention(tmpDir.map { it.dir("publications-store") })
    }
  }

  private fun BuildServiceRegistry.registerDevPubService(): Provider<DevPublishService> {
    return registerIfAbsent(
      SERVICE_NAME,
      DevPublishService::class
    ) {
      maxParallelUsages.set(1)
    }
  }

  /** React to [MavenPublishPlugin], and configure the appropriate DevPublish tasks */
  private fun configureMavenPublishingPlugin(
    project: Project,
    devPubExtension: DevPublishPluginExtension,
    devPubTasks: DevPublishTasksContainer,
    devPublishService: Provider<DevPublishService>,
  ) {
    project.plugins.withType<MavenPublishPlugin>().configureEach {
      project.extensions.configure<PublishingExtension> {
        repositories.maven(devPubExtension.stagingDevMavenRepo) {
          name = DEV_PUB__MAVEN_REPO_NAME
        }

        devPubTasks.generatePublicationChecksum.configure {
          publicationData.addAllLater(devPublishService.map { service ->
            publications
              .withType<MavenPublication>()
              .mapNotNull { service.createPublicationData(it) }
          })
        }
      }
    }
  }

  private fun PublishToMavenRepository.configurePublishToMavenRepositoryTask(
    devPubExtension: DevPublishPluginExtension,
    devPubService: Provider<DevPublishService>,
  ) {
    // register the service to ensure multiple PublishToMavenRepository tasks don't run in parallel
    usesService(devPubService)

    val stagingDevMavenRepo = devPubExtension.stagingDevMavenRepo
    val publicationStore = devPubExtension.publicationsStore.dir(this@configurePublishToMavenRepositoryTask.name)
    val checksumsStore = devPubExtension.checksumsStore

    // need to determine the repo lazily because the repo isn't set immediately
    val repoIsDevPub = providers.provider { repository?.name == DEV_PUB__MAVEN_REPO_NAME }.orElse(false)
    inputs.property("repoIsDevPub", repoIsDevPub)

    inputs
      // must convert to FileTree, because the directory might not exist, and
      // Gradle won't accept directories that don't exist as inputs.
      .files(checksumsStore.asFileTree)
      .withPropertyName("devPubChecksumsStoreFiles")

    outputs
      .files(publicationStore.map { it.asFileTree })
      .withPropertyName("devPubPublicationStore")

    val publicationData = devPubService.flatMap { service ->
      providers.provider { service.createPublicationData(publication) }
    }
    val currentChecksum: Provider<String> = publicationData.map { data ->
      data.createChecksumContent()
    }

    val storedChecksum: Provider<String> = providers.zip(
      publicationData,
      checksumsStore,
    ) { data, checksumStore ->
      checksumStore.asFile
        .resolve(data?.checksumFilename ?: "unknown")
        .takeIf(File::exists)
        ?.readText()
    }

    onlyIf("current checksums don't match stored checksum") {
      if (!repoIsDevPub.get()) return@onlyIf true

      if (logger.isInfoEnabled) {
        logger.info(checksumsToDebugString(currentChecksum, storedChecksum))
      }

      currentChecksum.orNull != storedChecksum.orNull
    }

    doFirst("clear staging repo") {
      if (repoIsDevPub.get()) {
        // clear the staging repo so that we can only sync this publication's files in the doLast {} below
        fs.delete { delete(stagingDevMavenRepo) }
        stagingDevMavenRepo.get().asFile.mkdirs()
      }
    }

    doLast("sync staging repo to publication store") {
      if (repoIsDevPub.get()) {
        logger.info("[$path] Syncing staging-dev-maven-repo to publication store ${publicationStore.get().asFile.invariantSeparatorsPath}")
        fs.sync {
          from(stagingDevMavenRepo)
          into(publicationStore)
        }

        // clear the staging repo for the next task
        logger.info("[$path] clearing staging-dev-maven-repo after publication")
        fs.delete { delete(stagingDevMavenRepo) }
        stagingDevMavenRepo.get().asFile.mkdirs()
      }
    }
  }

  /** React to [LifecycleBasePlugin], and configure the appropriate tasks */
  private fun configureBasePlugin(
    project: Project,
    devPubTasks: DevPublishTasksContainer,
  ) {
    project.plugins.withType<LifecycleBasePlugin>().configureEach {
      project.tasks.named(CHECK_TASK_NAME).configure {
        mustRunAfter(devPubTasks.publishAllToDevRepo)
        mustRunAfter(devPubTasks.generatePublicationChecksum)
        mustRunAfter(devPubTasks.updateDevRepo)
      }
    }
  }

  companion object {
    const val DEV_PUB__EXTENSION_NAME = "devPublish"

    /**
     * Name of the [org.gradle.api.artifacts.repositories.MavenArtifactRepository]
     * used for publishing test publications.
     */
    const val DEV_PUB__MAVEN_REPO_NAME = "DevPublishMaven"

    const val DEV_PUB__MAVEN_REPO_DIR = "maven-dev"

    const val DEV_PUB__PUBLICATION_DEPENDENCIES = "devPublication"
    const val DEV_PUB__PUBLICATION_INCOMING = "devPublicationResolvableElements"
    const val DEV_PUB__PUBLICATION_OUTGOING = "devPublicationConsumableElements"
  }
}
