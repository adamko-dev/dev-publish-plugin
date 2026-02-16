package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.data.DevPubAttributes
import dev.adamko.gradle.dev_publish.data.DevPubConfigurationsContainer
import dev.adamko.gradle.dev_publish.data.PublicationData
import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import dev.adamko.gradle.dev_publish.internal.checksums.CreatePublicationChecksum.Companion.createPublicationChecksum
import dev.adamko.gradle.dev_publish.internal.checksums.LoadPublicationChecksum.Companion.loadPublicationChecksum
import dev.adamko.gradle.dev_publish.internal.checksums.checksumsToDebugString
import dev.adamko.gradle.dev_publish.services.DevPublishService
import dev.adamko.gradle.dev_publish.services.DevPublishService.Companion.SERVICE_NAME
import dev.adamko.gradle.dev_publish.tasks.DevPublishTasksContainer
import dev.adamko.gradle.dev_publish.utils.*
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME

/**
 * Utility plugin for publishing subprojects to a local file-based Maven repository.
 *
 * The file-based repo (the location can be obtained from [DevPublishPluginExtension.devMavenRepo]) can be used in
 * functional tests, for example when using
 * [Gradle TestKit](https://docs.gradle.org/current/userguide/test_kit.html).
 *
 * This is useful for testing, as Maven metadata and Plugin Marker artifact gets published correctly.
 */
class DevPublishPlugin
@Inject
@DevPublishInternalApi
constructor(
  private val providers: ProviderFactory,
  private val layout: ProjectLayout,
  private val fs: FileSystemOperations,
  private val objects: ObjectFactory,
) : Plugin<Project> {

  override fun apply(project: Project) {
    val devPubExtension = project.extensions.createDevPublishExtension()

    val devPubService = project.gradle.sharedServices.registerDevPubService(project.path)

    val devPubTasks = DevPublishTasksContainer(
      tasks = project.tasks,
      devPubExtension = devPubExtension,
      objects = objects,
    )

    val devPubAttributes = DevPubAttributes(objects)

    val devPubConfigurations = DevPubConfigurationsContainer(
      devPubAttributes = devPubAttributes,
      dependencies = project.dependencies,
      configurations = project.configurations,
    )

    devPubTasks.updateDevRepo.configure {
      // update this project's maven-test-repo with files from other subprojects
      repositoryContents.from(devPubConfigurations.devMavenPublicationResolver)
    }

    devPubConfigurations.devMavenPublicationApiElements.outgoing {
      // Only share repos from _this_ subproject, not from the aggregated repo
      artifact(devPubExtension.publicationsStore) {
        builtBy(devPubTasks.publishAllToDevRepo)
      }
    }

    configureMavenPublishingPlugin(
      project = project,
      devPubExtension = devPubExtension,
      devPubTasks = devPubTasks,
    )

    configureBasePlugin(
      project = project,
      devPubTasks = devPubTasks,
    )

    project.tasks.withType<PublishToMavenRepository>().configureEach {
      if (name.endsWith("To${DEV_PUB__MAVEN_REPO_NAME}Repository")) {
        configurePublishToMavenRepositoryTask(devPubExtension, devPubService)
      }
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

  /**
   * Register a server per subproject, to prevent parallel publications into the same
   * [DevPublishPluginExtension.stagingDevMavenRepo].
   */
  private fun BuildServiceRegistry.registerDevPubService(
    projectPath: String
  ): Provider<DevPublishService> =
    registerIfAbsent("${SERVICE_NAME}_$projectPath", DevPublishService::class) {
      maxParallelUsages.set(1)
    }

  /** React to [MavenPublishPlugin], and configure the appropriate DevPublish tasks. */
  private fun configureMavenPublishingPlugin(
    project: Project,
    devPubExtension: DevPublishPluginExtension,
    devPubTasks: DevPublishTasksContainer,
  ) {
    project.plugins.withType<MavenPublishPlugin>().configureEach {
      project.extensions.configure<PublishingExtension> {
        repositories.maven(devPubExtension.stagingDevMavenRepo) {
          name = DEV_PUB__MAVEN_REPO_NAME
        }

        devPubTasks.generatePublicationChecksum.configure {
          publicationData.addAllLater(providers.provider {
            publications
              .withType<MavenPublication>()
              .mapNotNull { publication ->
                createPublicationData(
                  project = project,
                  publication = publication,
                )
              }
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
      // Must convert to FileTree, because the directory might not exist, and
      // Gradle won't accept directories that don't exist as inputs.
      .files(checksumsStore.sortedFiles())
      .withPropertyName("devPubChecksumsStoreFiles")
      .withPathSensitivity(RELATIVE)

    outputs
      .dir(publicationStore)
      .withPropertyName("devPubPublicationStore")

    val currentProjectDir = layout.projectDirectory

    val publicationData = providers.provider {
      createPublicationData(
        project = project,
        publication = publication,
      )
    }

    val currentChecksum = providers.createPublicationChecksum {
      this.projectDir.set(currentProjectDir)
      this.identifier.set(publicationData.flatMap { it.identifier })
    }

    val storedChecksum = providers.loadPublicationChecksum {
      this.checksumFilename.set(publicationData.map { it.checksumFilename })
      this.checksumsStore.set(checksumsStore)
    }

    onlyIf_("current checksums don't match stored checksum") {
      if (!repoIsDevPub.get()) {
        true
      } else {
        val enabled = currentChecksum.orNull != storedChecksum.orNull
        logger.info {
          val checksums = checksumsToDebugString(currentChecksum, storedChecksum).prependIndent("  ")
          val match = if (!enabled) "match" else "do not match"
          "[$path] currentChecksum and storedChecksum $match\n${checksums}"
        }
        enabled
      }
    }

    outputs.cacheIf("do not cache - this task only performs simple file modifications") { _ ->
      false
    }

    doFirst_("clear staging repo") {
      if (repoIsDevPub.get()) {
        // clear the staging repo so that we can only sync this publication's files in the doLast {} below
        fs.delete { delete(stagingDevMavenRepo) }
        stagingDevMavenRepo.get().asFile.mkdirs()
      }
    }

    doLast_("sync staging repo to publication store") {
      if (repoIsDevPub.get()) {
        logger.info { ("[$path] Syncing staging-dev-maven-repo to publication store ${publicationStore.get().asFile.invariantSeparatorsPath}") }
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

  /** Create an instance of [PublicationData] from [publication]. */
  private fun createPublicationData(
    project: Project,
    publication: MavenPublication?,
  ): PublicationData? {
    if (publication == null) {
      logger.warn("cannot create PublicationData - MavenPublication is null")
      return null
    }

    val identifier = providers.provider { publication.run { "$groupId:$artifactId:$version" } }

    val gmm = objects.fileCollection()
    project.tasks
      .withType<GenerateModuleMetadata>()
      .matching { task ->
        task.name == publication.getGenerateModuleMetadataTaskName()
      }
      .all {
        gmm.from(outputFile)
      }

    return objects.newInstance<PublicationData>(publication.name).apply {
      this.identifier.set(identifier)
      this.gradleModuleMetadata.from(gmm)
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
    const val DEV_PUB__PUBLICATION_API_DEPENDENCIES = "devPublicationApi"
    const val DEV_PUB__PUBLICATION_INCOMING = "devPublicationResolvableElements"
    const val DEV_PUB__PUBLICATION_OUTGOING = "devPublicationConsumableElements"

    private val logger = Logging.getLogger(DevPublishService::class.java)

    private fun MavenPublication.getGenerateModuleMetadataTaskName(): String =
      "generateMetadataFileFor${name.uppercaseFirstChar()}Publication"
  }
}
