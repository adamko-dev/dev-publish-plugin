package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.devPublishCapabilityNotation
import dev.adamko.gradle.dev_publish.utils.groupProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

class DevPublishCapabilityNotationTest : FunSpec({

  test("expect the group is appended to the DevPublish group") {
    project(name = "lib", group = "com.example").notation() shouldBe
        "dev.adamko.dev-publish.com.example:lib-dev-publish"
  }

  test("expect a project without a group is still valid coordinates") {
    project(name = "my-build", group = "").notation() shouldBe
        "dev.adamko.dev-publish:my-build-dev-publish"
  }

  test("expect the group is read lazily, after the build script sets it") {
    val project = project(name = "lib", group = "x.y.z")
    val notation = devPublishCapabilityNotation(project.groupProvider, project.name)

    project.group = "com.example"

    notation.orNull shouldBe "dev.adamko.dev-publish.com.example:lib-dev-publish"
  }

  test("expect projects differing only by group do not collide") {
    val bazANotation = project(name = "baz", group = "com.example.a").notation()
    val bazBNotation = project(name = "baz", group = "com.example.b").notation()
    bazANotation shouldNotBe bazBNotation
  }

  test("expect projects differing only by name do not collide") {
    val fooNotation = project(name = "foo", group = "com.example").notation()
    val barNotation = project(name = "bar", group = "com.example").notation()
    fooNotation shouldNotBe barNotation
  }
})


private fun project(name: String, group: String): Project =
  ProjectBuilder.builder()
    .withName(name)
    .build()
    .also { it.group = group }

private fun Project.notation(): String =
  devPublishCapabilityNotation(groupProvider, name).get()
