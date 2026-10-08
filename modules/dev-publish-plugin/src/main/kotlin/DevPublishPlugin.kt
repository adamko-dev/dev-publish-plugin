@file:Suppress("UnstableApiUsage")

package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.data.DevPubAttributes
import dev.adamko.gradle.dev_publish.data.DevPubConfigurationsContainer
import dev.adamko.gradle.dev_publish.internal.*
import dev.adamko.gradle.dev_publish.services.DevPublishService
import dev.adamko.gradle.dev_publish.services.DevPublishService.Companion.SERVICE_NAME
import dev.adamko.gradle.dev_publish.tasks.DevPublishTasksContainer
import dev.adamko.gradle.dev_publish.utils.*
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.problems.ProblemReporter
import org.gradle.api.problems.Problems
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin

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
  problems: Problems,
) : Plugin<Project> {

  private val problemsReporter: ProblemReporter = problems.reporter

  override fun apply(project: Project) {
    val devPublishDependency = createDevPublishDependency(project)

    val devPubExtension = project.extensions.createDevPublishExtension(devPublishDependency)

    val devPubService = project.gradle.sharedServices.registerDevPubService(project.path)

    val devPubTasks = DevPublishTasksContainer(
      tasks = project.tasks,
      devPubExtension = devPubExtension,
      objects = objects,
      problemsReporter = problemsReporter,
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

    devPubConfigurations.devMavenPublicationElements.configure {
      outgoing {
        // Only share repos from _this_ subproject, not from the aggregated repo
        artifact(devPubExtension.publicationsStore) {
          builtBy(devPubTasks.publishAllToDevRepo)
        }
      }
    }

    devPubConfigurations.devPublicationDependencies.configure {
      defaultDependencies {
        add(createProjectDependency(project))
      }
    }

    configureDevMavenRepoMetadata(
      project = project,
      devPubExtension = devPubExtension,
      devPubTasks = devPubTasks,
      devPubConfigurations = devPubConfigurations,
    )

    configureMavenPublishingPlugin(
      project = project,
      devPubExtension = devPubExtension,
      devPubTasks = devPubTasks,
    )

    configureBasePlugin(
      project = project,
      devPubTasks = devPubTasks,
    )

    configureSigningPlugin(
      project = project,
    )

    project.tasks.withType<PublishToMavenRepository>().configureEach {
      if (name.endsWith("To${DEV_PUB__MAVEN_REPO_NAME}Repository")) {
        configurePublishToMavenRepositoryTask(devPubExtension, devPubService)
      }
    }
  }

  private fun ExtensionContainer.createDevPublishExtension(
    devPublishDependency: Provider<Dependency>,
  ): DevPublishPluginExtension {
    return create<DevPublishPluginExtension>(DEV_PUB__EXTENSION_NAME).apply {
      devMavenRepo.convention(layout.buildDirectory.dir(DEV_PUB__MAVEN_REPO_DIR))

      val tmpDir = layout.buildDirectory.dir("tmp/.$DEV_PUB__MAVEN_REPO_DIR/")

      devMavenRepoMetadataDir.convention(tmpDir.map { it.dir("metadata") })
      stagingDevMavenRepo.convention(tmpDir.map { it.dir("staging") })
      @Suppress("DEPRECATION")
      checksumsStore.convention(tmpDir.map { it.dir("checksum-store") })
      publicationsStore.convention(tmpDir.map { it.dir("publications-store") })
      dependency.convention(devPublishDependency)
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

  /**
   * Expose the generated metadata file, and `dev-publish-utils`, through a consumable
   * Configuration, so [DevPublishPluginExtension.dependency] wires up a test source set on its own.
   */
  private fun configureDevMavenRepoMetadata(
    project: Project,
    devPubExtension: DevPublishPluginExtension,
    devPubTasks: DevPublishTasksContainer,
    devPubConfigurations: DevPubConfigurationsContainer,
  ) {
    devPubConfigurations.devPublishElements.configure {
      outgoing {
        artifact(devPubExtension.devMavenRepoMetadataDir) {
          builtBy(devPubTasks.generateDevPublishMetadata)
        }
        // Distinguishes this from `runtimeElements`.
        // Without this, the TargetJvmVersion attribute is required, and it's impossible to guess the required value.
        capability(
          devPublishCapabilityNotation(project.groupProvider, project.name).map { notation ->
            "$notation:$DevPublishVersion"
          }
        )
      }
    }
  }

  /** React to [MavenPublishPlugin], and configure the appropriate Dev Publish tasks. */
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

    // need to determine the repo lazily because the repo isn't set immediately
    val repoIsDevPub = providers.provider { repository?.name == DEV_PUB__MAVEN_REPO_NAME }.orElse(false)
    inputs.property("repoIsDevPub", repoIsDevPub)

    // Gradle already tracks every publishable file as an input,
    // so declaring an output is enough to make the task up-to-date when the publication is unchanged.
    outputs
      .dir(publicationStore)
      .withPropertyName("devPubPublicationStore")

    outputs.doNotCacheIf("this task only performs simple file modifications") { _ ->
      true
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

  /**
   * React to the [SigningPlugin].
   *
   * If signing is enabled and there's no signatory, then Dev Publish tasks will fail.
   * To improve UX:
   * - Throw an error that explains the situation and suggest solutions.
   * - Create `signing.publishingOutsideDevRepo` helper, for use in [SigningExtension.setRequired].
   */
  private fun configureSigningPlugin(
    project: Project,
  ) {
    project.plugins.withType<SigningPlugin>().configureEach {
      val publishingToDevRepo = publishesToDevRepo(project)
      val publishingOutsideDevRepo = project.publishesOutsideDevRepo()

      project.extensions.configure<SigningExtension> {
        if (this !is ExtensionAware) {
          problemsReporter.reportSigningExtensionNotExtensionAware()
          return@configure
        }

        extensions.add<Provider<Boolean>>(
          SIGNING__EXTERNAL_PUBLISHING_PROPERTY,
          publishingOutsideDevRepo,
        )
      }

      project.tasks.withType<Sign>().configureEach {
        doFirst_("report missing signatory") {
          // only when the dev repo is the sole target - otherwise signing is genuinely required,
          // and Gradle's own 'no configured signatory' error is the correct one
          if (isRequired && signatory == null &&
            publishingToDevRepo.get() && !publishingOutsideDevRepo.get()
          ) {
            problemsReporter.failMissingSignatory(taskPath = path)
          }
        }
      }
    }
  }

  /**
   * `true` if this project has a task in the graph that publishes to the
   * [Dev Publish][DEV_PUB__MAVEN_REPO_NAME] Maven repository.
   */
  private fun publishesToDevRepo(project: Project): Provider<Boolean> {
    val publishesToDevRepo = objects.property<Boolean>().convention(false)

    val devRepoSuffix = "To${DEV_PUB__MAVEN_REPO_NAME}Repository"

    val devRepoPublishTasks = project.tasks
      .withType<PublishToMavenRepository>()
      .matching { it.name.endsWith(devRepoSuffix) }

    project.gradle.taskGraph.whenReady {
      publishesToDevRepo.set(devRepoPublishTasks.any { hasTask(it) })
    }

    publishesToDevRepo.finalizeValueOnRead()

    return publishesToDevRepo
  }

  /**
   * `true` if this project has a task in the graph that publishes somewhere other than the
   * Dev Publish Maven repository.
   *
   * Only this project's own tasks are considered, because a [Sign] task and the publications it
   * signs belong to the same project. Defaults to `true`, so signing is never skipped by accident.
   */
  private fun Project.publishesOutsideDevRepo(): Provider<Boolean> {
    val publishesOutsideDevRepo = objects.property<Boolean>().convention(true)

    val devRepoSuffix = "To${DEV_PUB__MAVEN_REPO_NAME}Repository"

    val publishesOutside = tasks
      .withType<AbstractPublishToMaven>()
      .matching { task ->
        when (task) {
          is PublishToMavenLocal -> true
          is PublishToMavenRepository -> !task.name.endsWith(devRepoSuffix)
          else -> false
        }
      }

    gradle.taskGraph.whenReady {
      publishesOutsideDevRepo.set(publishesOutside.any { hasTask(it) })
    }

    publishesOutsideDevRepo.finalizeValueOnRead()

    return publishesOutsideDevRepo
  }

  /** React to [LifecycleBasePlugin], and configure the appropriate tasks */
  private fun configureBasePlugin(
    project: Project,
    devPubTasks: DevPublishTasksContainer,
  ) {
    project.plugins.withType<LifecycleBasePlugin>().configureEach {
      project.tasks.named(CHECK_TASK_NAME).configure {
        mustRunAfter(devPubTasks.publishAllToDevRepo)
        mustRunAfter(devPubTasks.updateDevRepo)
      }
    }
  }

  /** Create an instance of [dev.adamko.gradle.dev_publish.data.PublicationData] from [publication]. */
  @Suppress("DEPRECATION")
  private fun createPublicationData(
    project: Project,
    publication: MavenPublication?,
  ): dev.adamko.gradle.dev_publish.data.PublicationData? {
    if (publication == null) {
      problemsReporter.reportPublicationNotSet()
      return null
    }

    val identifier = providers.provider { publication.run { "$groupId:$artifactId:$version" } }

    val gmm = getGmm(project, publication)

    return objects.newInstance<dev.adamko.gradle.dev_publish.data.PublicationData>(publication.name).apply {
      this.identifier.set(identifier)
      this.gradleModuleMetadata.from(gmm)
    }
  }


  private fun getGmm(
    project: Project,
    publication: MavenPublication,
  ): FileCollection {
    val gmm = objects.fileCollection()
    project.tasks
      .withType<GenerateModuleMetadata>()
      .all {
        if (name == publication.getGenerateModuleMetadataTaskName()) {
          gmm.from(outputFile)
        }
      }
    return gmm
  }

  companion object {
    const val DEV_PUB__EXTENSION_NAME = "devPublish"

    /**
     * Name of the [org.gradle.api.artifacts.repositories.MavenArtifactRepository]
     * used for publishing test publications.
     */
    const val DEV_PUB__MAVEN_REPO_NAME = "DevPublishMaven"

    const val DEV_PUB__MAVEN_REPO_DIR = "maven-dev"

    /** Name of the consumable Configuration behind [DevPublishPluginExtension.dependency]. */
    internal const val DEV_PUB__ELEMENTS_CONFIGURATION = "devPublishElements"

    /** Name of the extension Dev Publish adds to [SigningExtension]. */
    internal const val SIGNING__EXTERNAL_PUBLISHING_PROPERTY = "publishingOutsideDevRepo"

    /** Group of the capability that identifies [DEV_PUB__ELEMENTS_CONFIGURATION]. */
    internal const val DEV_PUB__CAPABILITY_GROUP = "dev.adamko.dev-publish"

    /** Coordinates of the helper library that [DevPublishPluginExtension.dependency] brings in. */
    internal const val DEV_PUB__UTILS_GROUP = "dev.adamko.gradle"

    /** @see DEV_PUB__UTILS_GROUP */
    internal const val DEV_PUB__UTILS_MODULE = "dev-publish-utils"

    internal const val DEV_PUB__UTILS_CONFIGURATION = "devPublishUtils"

    const val DEV_PUB__PUBLICATION_DEPENDENCIES = "devPublication"

    @Deprecated(
      "devPublication dependencies are shared with consumers by default, so devPublicationApi is redundant. " +
          "Scheduled for removal in version 2.0.",
      ReplaceWith("DEV_PUB__PUBLICATION_DEPENDENCIES"),
    )
    const val DEV_PUB__PUBLICATION_API_DEPENDENCIES = "devPublicationApi"
    const val DEV_PUB__PUBLICATION_INCOMING = "devPublicationResolvableElements"
    const val DEV_PUB__PUBLICATION_OUTGOING = "devPublicationConsumableElements"

    /**
     * The [ProjectDependency] handed to users as [DevPublishPluginExtension.dependency].
     *
     * Targets [DEV_PUB__ELEMENTS_CONFIGURATION] of this same project, selected by capability
     * because `runtimeElements` matches the same attributes and would otherwise be ambiguous.
     */
    private fun createDevPublishDependency(project: Project): Provider<Dependency> {
      return devPublishCapabilityNotation(group = project.groupProvider, name = project.name).map { capability ->
        createProjectDependency(project).apply {
          capabilities {
            requireCapability(capability)
          }
          because("the Dev Publish dev Maven repository, and the dev-publish-utils library that reads it")
        }
      }
    }

    /**
     * Coordinates of the capability that distinguishes [DEV_PUB__ELEMENTS_CONFIGURATION] from the
     * project's other variants.
     *
     * Derived from the project's own coordinates, which are already required to be unique.
     */
    internal fun devPublishCapabilityNotation(group: Provider<String>, name: String): Provider<String> =
      group.map { group ->
        buildString {
          append(DEV_PUB__CAPABILITY_GROUP)
          if (group.isNotEmpty()) {
            append(".")
            append(group)
          }
          append(":")
          append(name)
          append("-dev-publish")
        }
      }

    private fun MavenPublication.getGenerateModuleMetadataTaskName(): String =
      "generateMetadataFileFor${name.uppercaseFirstChar()}Publication"
  }
}
