import buildsrc.utils.excludeProjectConfigurationDirs
import buildsrc.utils.skipTestFixturesPublications
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  buildsrc.conventions.`kotlin-gradle-plugin`
  `java-test-fixtures`
  idea
}

project.version = "0.5.0-SNAPSHOT"
project.group = "dev.adamko.gradle"

dependencies {
  testFixturesApi(gradleTestKit())
  testFixturesApi(platform(libs.kotest.bom))
  testFixturesApi(libs.kotest.runnerJUnit5)
  testFixturesApi(libs.kotest.assertionsCore)
}

kotlin {
  jvmToolchain(17)
  @OptIn(ExperimentalAbiValidation::class)
  abiValidation {
    enabled = true
    variants.configureEach {
      tasks.check {
        dependsOn(legacyDump.legacyCheckTaskProvider)
      }
    }
    filters {
      excluded {
        annotatedWith.add("dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi")
      }
    }
  }
}

gradlePlugin {
  isAutomatedPublishing = true
  website = "https://github.com/adamko-dev/dev-publish-plugin"
  vcsUrl = "https://github.com/adamko-dev/dev-publish-plugin.git"

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
      "verify",
      "check",
      "functional-test",
      "integration-test",
      "publication",
    )
  }
}

val testMavenRepoDir: Provider<Directory> = layout.buildDirectory.dir("test-maven-repo")
val projectTestTempDir: Provider<Directory> = layout.buildDirectory.dir("project-tests")

publishing {
  repositories {
    maven(testMavenRepoDir) {
      name = "TestMavenRepo"
    }
  }
}

skipTestFixturesPublications()

tasks.withType<Test>().configureEach {
  dependsOn("publishAllPublicationsToTestMavenRepoRepository")
  systemProperty("testMavenRepoDir", testMavenRepoDir.get().asFile.invariantSeparatorsPath)
  systemProperty("projectTestTempDir", projectTestTempDir.get().asFile.invariantSeparatorsPath)
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    optIn.addAll(
      "dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi",
    )
  }
}

idea {
  module {
    excludeProjectConfigurationDirs(layout, providers)
    excludeDirs.addAll(
      layout.files(
        ".idea",
        "gradle/wrapper",
      )
    )
  }
}

tasks.nmcpPublishAllPublicationsToCentralPortal {
  val isReleaseVersion = mavenPublishing.isReleaseVersion
  onlyIf("is release version") { isReleaseVersion.get() }
}
tasks.nmcpPublishAllPublicationsToCentralPortalSnapshots {
  val isReleaseVersion = mavenPublishing.isReleaseVersion
  onlyIf("is snapshot version") { !isReleaseVersion.get() }
}

tasks.register("nmcpPublish") {
  group = PublishingPlugin.PUBLISH_TASK_GROUP
  dependsOn(tasks.nmcpPublishAllPublicationsToCentralPortal)
  dependsOn(tasks.nmcpPublishAllPublicationsToCentralPortalSnapshots)
}
