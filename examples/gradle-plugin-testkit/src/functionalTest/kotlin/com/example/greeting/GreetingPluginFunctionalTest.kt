package com.example.greeting

import dev.adamko.gradle.dev_publish.devMavenRepo
import dev.adamko.gradle.dev_publish.devPublishSettings
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir

/**
 * Applies the plugin the way a real user would, resolved from a Maven
 * repository, rather than with `withPluginClasspath()`.
 */
class GreetingPluginFunctionalTest {

  private val devMavenRepo: Path = devMavenRepo()

  @Test
  fun `plugin can be resolved by id and version`(@TempDir projectDir: Path) {
    // Writes a settings file that resolves the plugin under test from the dev Maven repo.
    projectDir.resolve("settings.gradle.kts").writeText(
      devPublishSettings(rootProjectName = "greeting-consumer")
    )

    projectDir.resolve("build.gradle.kts").writeText(
      """
      |plugins {
      |  id("com.example.greeting") version "1.0.0"
      |}
      |""".trimMargin()
    )

    val result = GradleRunner.create()
      .withProjectDir(projectDir.toFile())
      .withArguments("greeting", "--stacktrace")
      .forwardOutput()
      .build()

    assertTrue(
      result.output.contains("Hello from GreetingPlugin"),
      "expected the plugin's task to run",
    )
  }

  @Test
  fun `dev maven repo contains the plugin marker artifact`() {
    val marker = devMavenRepo
      .resolve("com/example/greeting/com.example.greeting.gradle.plugin/1.0.0")
    assertTrue(marker.isDirectory(), "expected a Plugin Marker Artifact at $marker")
  }
}
