package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__UTILS_CONFIGURATION
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.kotlin.dsl.*
import org.gradle.testfixtures.ProjectBuilder

/**
 * Compiling tests must not trigger dev publishing.
 * Only the test runtime needs the dev repo metadata.
 */
class DevPublishDependencyUsageTest : FunSpec({

  val project = ProjectBuilder.builder()
    .withName("foo-project")
    .build()

  with(project) {
    plugins.apply(JavaLibraryPlugin::class)
    plugins.apply(DevPublishPlugin::class)

    // A local file instead of the published module, so resolving needs no repositories.
    dependencies.add(DEV_PUB__UTILS_CONFIGURATION, files("dev-publish-utils.jar"))
    dependencies.addProvider("testImplementation", extensions.getByType<DevPublishPluginExtension>().dependency())
  }

  val metadataDir = project.layout.buildDirectory.dir("tmp/.maven-dev/metadata").get().asFile
  val utilsJar = project.file("dev-publish-utils.jar")

  context("test compile classpath") {
    val testCompileClasspath = project.configurations.getByName("testCompileClasspath")

    test("expect it contains dev-publish-utils, but not the dev repo metadata") {
      testCompileClasspath.files shouldContainExactlyInAnyOrder listOf(utilsJar)
    }

    test("expect it does not depend on generating the dev repo metadata") {
      val taskNames = testCompileClasspath.buildDependencies.getDependencies(null).map { it.name }
      taskNames shouldNotContain "generateDevPublishMetadata"
    }
  }

  context("test runtime classpath") {
    val testRuntimeClasspath = project.configurations.getByName("testRuntimeClasspath")

    test("expect it contains dev-publish-utils and the dev repo metadata") {
      testRuntimeClasspath.files shouldContainExactlyInAnyOrder listOf(utilsJar, metadataDir)
    }

    test("expect it depends on generating the dev repo metadata") {
      val taskNames = testRuntimeClasspath.buildDependencies.getDependencies(null).map { it.name }
      taskNames shouldContain "generateDevPublishMetadata"
    }
  }
})
