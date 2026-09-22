package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.devPublishCapabilityNotation
import dev.adamko.gradle.dev_publish.utils.groupProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

class DevPublishCapabilityNotationTest : FunSpec({

  fun project(name: String, group: String? = null): Project =
    ProjectBuilder.builder().withName(name).build().also { if (group != null) it.group = group }

  fun Project.notation(): String =
    devPublishCapabilityNotation(groupProvider, name).get()

  test("expect the group is appended to the DevPublish group") {
    project(name = "lib", group = "com.example").notation() shouldBe
        "dev.adamko.dev-publish.com.example:lib-dev-publish"
  }

  test("expect a project without a group is still valid coordinates") {
    project(name = "my-build").notation() shouldBe
        "dev.adamko.dev-publish:my-build-dev-publish"
  }

  test("expect the group is read lazily, after the build script sets it") {
    val project = project(name = "lib")
    val notation = devPublishCapabilityNotation(project.groupProvider, project.name)

    project.group = "com.example"

    notation.get() shouldBe "dev.adamko.dev-publish.com.example:lib-dev-publish"
  }

  test("expect projects differing only by group do not collide") {
    project(name = "baz", group = "com.example.a").notation() shouldNotBe
        project(name = "baz", group = "com.example.b").notation()
  }

  test("expect projects differing only by name do not collide") {
    project(name = "foo", group = "com.example").notation() shouldNotBe
        project(name = "bar", group = "com.example").notation()
  }
})