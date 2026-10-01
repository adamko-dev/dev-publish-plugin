plugins {
  buildsrc.conventions.`kotlin-jvm-library`
}

description = "Helpers for reading the DevPublish dev Maven repository from a test JVM."

dependencies {
  implementation(projects.modules.devPublishCommon)

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
}
