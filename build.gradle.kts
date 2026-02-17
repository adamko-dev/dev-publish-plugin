import buildsrc.utils.excludeProjectConfigurationDirs
import buildsrc.utils.skipTestFixturesPublications
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  buildsrc.conventions.`kotlin-gradle-plugin`
  `java-test-fixtures`
  idea
  id("com.gradleup.nmcp.aggregation")
}

project.version = "1.2.0-SNAPSHOT"
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

nmcpAggregation {
  centralPortal {
    username = mavenPublishing.mavenCentralUsername
    password = mavenPublishing.mavenCentralPassword

    // publish manually from the portal
    publishingType = "USER_MANAGED"
  }
}

dependencies {
  nmcpAggregation(project)
}

tasks.nmcpPublishAggregationToCentralPortal {
  val isReleaseVersion = mavenPublishing.isReleaseVersion
  onlyIf("is release version") { _ -> isReleaseVersion.get() }
}

tasks.nmcpPublishAggregationToCentralPortalSnapshots {
  val isReleaseVersion = mavenPublishing.isReleaseVersion
  onlyIf("is snapshot version") { _ -> !isReleaseVersion.get() }
}

tasks.register("nmcpPublish") {
  group = PublishingPlugin.PUBLISH_TASK_GROUP
  dependsOn(tasks.nmcpPublishAggregationToCentralPortal)
  dependsOn(tasks.nmcpPublishAggregationToCentralPortalSnapshots)
}
