package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.services.DevPublishService.Companion.SERVICE_NAME
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gradle.kotlin.dsl.*
import org.gradle.testfixtures.ProjectBuilder

/**
 * Dev publish tasks in one project share a staging directory,
 * so they must not run in parallel.
 */
class DevPublishServiceUsageTest : FunSpec({

  val project = ProjectBuilder.builder()
    .withName("foo-project")
    .build()
  project.plugins.apply(DevPublishPlugin::class)

  test("expect the service allows only one usage at a time") {
    val registration = project.gradle.sharedServices.registrations
      .getByName("${SERVICE_NAME}_${project.path}")

    registration.maxParallelUsages.orNull shouldBe 1
  }
})
