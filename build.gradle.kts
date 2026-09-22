import buildsrc.settings.MavenPublishingSettings
import buildsrc.utils.excludeProjectConfigurationDirs

plugins {
  buildsrc.conventions.base
  idea
  id("com.gradleup.nmcp.aggregation")
  id("dev.adamko.dev-publish")
  id("buildsrc.conventions.bootstrap")
}

group = "root" // differentiate between this project and the nested modules/dev-publish-plugin

//region publishing
// The root is not published, but the Central Portal credentials for the aggregation live here.
val mavenPublishing =
  extensions.create<MavenPublishingSettings>(MavenPublishingSettings.EXTENSION_NAME, project)

dependencies {
  nmcpAggregation(projects.modules.devPublishCommon)
  nmcpAggregation(projects.modules.devPublishPlugin)
  nmcpAggregation(projects.modules.devPublishUtils)
}

nmcpAggregation {
  allowDuplicateProjectNames.set(true)
  centralPortal {
    username = mavenPublishing.mavenCentralUsername
    password = mavenPublishing.mavenCentralPassword

    // publish manually from the portal
    publishingType = "USER_MANAGED"
  }
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
//endregion

configurations.bootstrapJars {
  extendsFrom(configurations.nmcpAggregation)
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
