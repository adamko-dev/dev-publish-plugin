import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
  buildsrc.conventions.`kotlin-jvm-library`
  id("dev.adamko.dev-publish")
}

description = "Common code shared between DevPublish libraries."

dependencies {
  testImplementation(platform(libs.kotest.bom))
  testImplementation(libs.kotest.runnerJUnit5)
  testImplementation(libs.kotest.assertionsCore)
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
