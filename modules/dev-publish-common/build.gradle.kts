@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
  buildsrc.conventions.`kotlin-jvm-library`
}

description = "Common code shared between DevPublish libraries."

publishing {
  repositories {
    maven(isolated.rootProject.projectDirectory.dir("build/test-maven-repo")) {
      name = "TestMavenRepo"
    }
  }
}

kotlin {
  compilerOptions {
    optIn.addAll(
      "dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi",
    )
  }
  @OptIn(ExperimentalAbiValidation::class)
  abiValidation {
    filters {
      exclude { annotatedWith.add("dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi") }
    }
  }
}
