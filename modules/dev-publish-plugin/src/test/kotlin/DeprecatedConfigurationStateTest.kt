package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_API_DEPENDENCIES
import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.DEV_PUB__PUBLICATION_DEPENDENCIES
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.gradle.internal.deprecation.DeprecatableConfiguration
import org.gradle.kotlin.dsl.apply
import org.gradle.testfixtures.ProjectBuilder

class DeprecatedConfigurationStateTest : FunSpec({

  val project = ProjectBuilder.builder().withName("foo-project").build()
  project.plugins.apply(DevPublishPlugin::class)

  test("expect the deprecated configuration names its replacement") {
    val devPubApi = project.configurations.getByName(@Suppress("DEPRECATION") DEV_PUB__PUBLICATION_API_DEPENDENCIES)

    devPubApi.shouldBeInstanceOf<DeprecatableConfiguration>()
      .declarationAlternatives shouldContain DEV_PUB__PUBLICATION_DEPENDENCIES
  }
})
