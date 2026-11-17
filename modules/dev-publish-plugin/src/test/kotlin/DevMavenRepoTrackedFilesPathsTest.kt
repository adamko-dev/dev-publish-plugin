package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.tasks.DevPublishTasksContainer.Companion.GENERATE_DEV_PUBLISH_METADATA_TASK_NAME
import dev.adamko.gradle.dev_publish.tasks.GenerateDevPublishMetadataTask
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.testfixtures.ProjectBuilder

class DevMavenRepoTrackedFilesPathsTest : FunSpec({

  context("when the dev repo has a release version") {
    val project = project()
    writeRepoFiles(
      project,
      "foo/lib/1.0.0/lib-1.0.0-sources.jar",
      "foo/lib/1.0.0/lib-1.0.0.jar",
      "foo/lib/1.0.0/lib-1.0.0.pom",
    )

    test("expect paths are unchanged") {
      project.tasks.generateDevPublishMetadata.shouldHaveTrackedFilesPaths(
        "foo/lib/1.0.0/lib-1.0.0-sources.jar",
        "foo/lib/1.0.0/lib-1.0.0.jar",
        "foo/lib/1.0.0/lib-1.0.0.pom",
      )
    }
  }

  context("when the dev repo has a SNAPSHOT version") {
    val project = project()
    writeRepoFiles(
      project,
      "foo/lib/1.0.0-SNAPSHOT/lib-1.0.0-20261113.215135-1-javadoc.jar",
      "foo/lib/1.0.0-SNAPSHOT/lib-1.0.0-20261113.215135-1-sources.jar",
      "foo/lib/1.0.0-SNAPSHOT/lib-1.0.0-20261113.215135-1.jar",
      "foo/lib/1.0.0-SNAPSHOT/lib-1.0.0-20261113.215135-1.module",
      "foo/lib/1.0.0-SNAPSHOT/lib-1.0.0-20261113.215135-1.pom",
    )

    test("expect timestamps are replaced with SNAPSHOT") {
      project.tasks.generateDevPublishMetadata.shouldHaveTrackedFilesPaths(
        "foo/lib/1.0.0-SNAPSHOT/lib-1.0.0-SNAPSHOT-javadoc.jar",
        "foo/lib/1.0.0-SNAPSHOT/lib-1.0.0-SNAPSHOT-sources.jar",
        "foo/lib/1.0.0-SNAPSHOT/lib-1.0.0-SNAPSHOT.jar",
        "foo/lib/1.0.0-SNAPSHOT/lib-1.0.0-SNAPSHOT.module",
        "foo/lib/1.0.0-SNAPSHOT/lib-1.0.0-SNAPSHOT.pom",
      )
    }
  }

  context("when a SNAPSHOT version is re-published with a new timestamp and build number") {
    val firstPublish = project()
    writeRepoFiles(
      firstPublish,
      "foo/lib/1.0.0-SNAPSHOT/lib-1.0.0-20261113.215135-1.pom",
    )
    val secondPublish = project()
    writeRepoFiles(
      secondPublish,
      "foo/lib/1.0.0-SNAPSHOT/lib-1.0.0-20261114.084757-12.pom",
    )

    test("expect the same paths") {
      val first = firstPublish.tasks.generateDevPublishMetadata.devMavenRepoTrackedFilesPaths.orNull
      val second = secondPublish.tasks.generateDevPublishMetadata.devMavenRepoTrackedFilesPaths.orNull
      first shouldBe second
    }
  }

  context("when a release version file name looks like a SNAPSHOT timestamp") {
    val project = project()
    writeRepoFiles(
      project,
      "foo/lib/1.0.0-20261113.215135-1/lib-1.0.0-20261113.215135-1.jar",
    )

    test("expect paths are unchanged") {
      project.tasks.generateDevPublishMetadata.shouldHaveTrackedFilesPaths(
        "foo/lib/1.0.0-20261113.215135-1/lib-1.0.0-20261113.215135-1.jar",
      )
    }
  }

  context("when the dev repo has excluded files") {
    val project = project()
    writeRepoFiles(
      project,
      "foo/lib/maven-metadata.xml",
      "foo/lib/1.0.0-SNAPSHOT/lib-1.0.0-20261113.215135-1.jar",
    )

    test("expect only tracked files") {
      project.tasks.generateDevPublishMetadata.shouldHaveTrackedFilesPaths(
        "foo/lib/1.0.0-SNAPSHOT/lib-1.0.0-SNAPSHOT.jar",
      )
    }
  }

  context("when the extension adds an excluded file pattern") {
    val project = project()
    project.extensions.getByType<DevPublishPluginExtension>()
      .excludedDevMavenRepoFilePatterns.add("**/*.spdx.json")
    writeRepoFiles(
      project,
      "foo/lib/maven-metadata.xml",
      "foo/lib/1.0.0/lib-1.0.0.jar",
      "foo/lib/1.0.0/lib-1.0.0.spdx.json",
    )

    test("expect the added and default patterns are excluded") {
      project.tasks.generateDevPublishMetadata.shouldHaveTrackedFilesPaths(
        "foo/lib/1.0.0/lib-1.0.0.jar",
      )
    }
  }
}) {
  companion object {
    private fun project(): Project {
      val project = ProjectBuilder.builder()
        .withName("foo-project")
        .build()
      project.plugins.apply(DevPublishPlugin::class)
      return project
    }

    private fun Project.devMavenRepo(): Path =
      extensions.getByType<DevPublishPluginExtension>().devMavenRepo.get().asFile.toPath()

    private fun writeRepoFiles(project: Project, vararg paths: String) {
      paths.forEach { path ->
        project.devMavenRepo().resolve(path)
          .createParentDirectories()
          .writeText(path)
      }
    }

    private fun GenerateDevPublishMetadataTask.shouldHaveTrackedFilesPaths(vararg paths: String) {
      devMavenRepoTrackedFilesPathsList.shouldContainExactly(*paths)
    }

    private val TaskContainer.generateDevPublishMetadata: GenerateDevPublishMetadataTask
      get() = named<GenerateDevPublishMetadataTask>(GENERATE_DEV_PUBLISH_METADATA_TASK_NAME)
        .orNull
        .shouldNotBeNull()

    private val GenerateDevPublishMetadataTask.devMavenRepoTrackedFilesPathsList: List<String>?
      get() = devMavenRepoTrackedFilesPaths.orNull?.toList()
  }
}
