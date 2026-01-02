package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__MAVEN_REPO_NAME
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.maps.shouldHaveKey
import io.kotest.matchers.maps.shouldNotHaveKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.kotlin.dsl.*
import org.gradle.testfixtures.ProjectBuilder

class ConfigurePublishTasksTest : FunSpec({
  context("given project with multiple publish tasks") {
    val project = ProjectBuilder.builder()
      .withName("foo-project")
      .build()

    with(project) {
      plugins.apply(JavaLibraryPlugin::class)
      plugins.apply(DevPublishPlugin::class)
      plugins.apply(MavenPublishPlugin::class)

      extensions.configure<PublishingExtension> {
        repositories {
          maven(layout.buildDirectory.dir("project-local-repo")) {
            name = "ProjectLocalRepo"
          }
        }
        publications.create<MavenPublication>("mavenJava") {
          from(project.components["java"])
        }
      }
    }

    val publishTasks = project.tasks.withType<PublishToMavenRepository>()

    test("should have publish tasks") {
      publishTasks.forAtLeastOne { task ->
        task.repository.name shouldBe "ProjectLocalRepo"
      }
      publishTasks.forAtLeastOne { task ->
        task.repository.name shouldBe DEV_PUB__MAVEN_REPO_NAME
      }
    }

    test("dev-publish should only configure dev-publish tasks") {
      publishTasks.forEach { task ->
        if (task.name.endsWith("To${DEV_PUB__MAVEN_REPO_NAME}Repository")) {
          task.inputs.properties shouldHaveKey "repoIsDevPub"
          task.inputs.properties["repoIsDevPub"]
            .shouldBeInstanceOf<Boolean>() shouldBe true
        } else {
          task.inputs.properties shouldNotHaveKey "repoIsDevPub"
        }
      }
    }
  }
})
