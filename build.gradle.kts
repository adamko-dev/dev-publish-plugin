import buildsrc.utils.excludeGeneratedGradleDsl
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  buildsrc.conventions.`kotlin-gradle-plugin`
  dev.adamko.kotlin.`binary-compatibility-validator`
  idea
}

project.version = "0.0.1"
project.group = "dev.adamko.gradle"

@Suppress("UnstableApiUsage")
gradlePlugin {
  isAutomatedPublishing = true
  website.set("https://github.com/adamko-dev/dev-publish-plugin")
  vcsUrl.set("https://github.com/adamko-dev/dev-publish-plugin.git")

  plugins.register("DevPublish") {
    id = "dev.adamko.dev-publish"
    displayName = "DevPublish"
    description = "Publish Gradle Projects to a project-local repository, for functional testing"
    implementationClass = "dev.adamko.gradle.dev_publish.DevPublishPlugin"
    tags.addAll(
      "maven",
      "publishing",
      "maven-publish",
      "test",
      "publication",
      "verify",
    )
  }
}


binaryCompatibilityValidator {
  ignoredMarkers.addAll(
    "dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi",
  )
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    freeCompilerArgs.addAll(
      "-opt-in=dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi",
    )
  }
}

idea {
  module {
    excludeGeneratedGradleDsl(layout)
    excludeDirs.addAll(
      layout.files(
        ".idea",
        "gradle/wrapper",
      )
    )
  }
}
