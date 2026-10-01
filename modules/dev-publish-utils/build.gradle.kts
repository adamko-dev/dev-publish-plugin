plugins {
  buildsrc.conventions.`kotlin-jvm-library`
}

description = "Helpers for reading the DevPublish dev Maven repository from a test JVM."

publishing {
  repositories {
    maven(isolated.rootProject.projectDirectory.dir("build/test-maven-repo")) {
      name = "TestMavenRepo"
    }
  }
}

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
