import buildsrc.utils.excludeGeneratedGradleDsl
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  buildsrc.conventions.`kotlin-gradle-plugin`
  dev.adamko.kotlin.`binary-compatibility-validator`
  idea
}

project.version = "0.0.1"
project.group = "dev.adamko.gradle"

gradlePlugin {
  plugins.create("GradlePublishingTest") {
    id = "dev.adamko.dev-publish"
    implementationClass = "dev.adamko.gradle.dev_publish.DevPublishPlugin"
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
