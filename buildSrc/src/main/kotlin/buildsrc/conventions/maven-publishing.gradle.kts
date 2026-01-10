package buildsrc.conventions

import buildsrc.settings.MavenPublishingSettings

plugins {
  `maven-publish`
  signing
  id("com.gradleup.nmcp")
}

val mavenPublishing =
  extensions.create<MavenPublishingSettings>(MavenPublishingSettings.EXTENSION_NAME, project)


//region POM convention
publishing {
  publications.withType<MavenPublication>().configureEach {
    pom {
      name.convention("Dev Publish Gradle Plugin")
      description.convention("Dev Publish is a Gradle plugin that publishes subprojects to a project-local directory, ready for functional testing.")
      url.convention("https://github.com/adamko-dev/dev-publish-plugin")

      scm {
        connection.convention("scm:git:https://github.com/adamko-dev/dev-publish-plugin")
        developerConnection.convention("scm:git:https://github.com/adamko-dev/dev-publish-plugin")
        url.convention("https://github.com/adamko-dev/dev-publish-plugin")
      }

      licenses {
        license {
          name.convention("Apache-2.0")
          url.convention("https://www.apache.org/licenses/LICENSE-2.0.txt")
        }
      }

      developers {
        developer {
          email.set("adam@adamko.dev")
        }
      }
    }
  }
}
//endregion


publishing {
  repositories {
    maven(rootProject.layout.buildDirectory.dir("project-repo")) {
      name = "ProjectRootBuild"
    }
  }
}


//region Maven Central publishing/signing
publishing {
  repositories {
    val adamkoDevUsername = mavenPublishing.adamkoDevUsername.orNull
    val adamkoDevPassword = mavenPublishing.adamkoDevPassword.orNull
    if (!adamkoDevUsername.isNullOrBlank() && !adamkoDevPassword.isNullOrBlank()) {
      maven(mavenPublishing.adamkoDevReleaseUrl) {
        name = "AdamkoDev"
        credentials {
          username = adamkoDevUsername
          password = adamkoDevPassword
        }
      }
    }
  }

  // com.gradle.plugin-publish automatically adds a Javadoc jar
}

signing {
  logger.info("maven-publishing.gradle.kts enabled signing for ${project.path}")

  val keyId = mavenPublishing.signingKeyId.orNull
  val key = mavenPublishing.signingKey.orNull
  val password = mavenPublishing.signingPassword.orNull

  val signingCredentialsPresent =
    !keyId.isNullOrBlank() && !key.isNullOrBlank() && !password.isNullOrBlank()

  if (signingCredentialsPresent) {
    logger.info("maven-publishing.gradle.kts enabled signing for ${project.displayName}")
    useInMemoryPgpKeys(keyId, key, password)
  }

  setRequired({
    signingCredentialsPresent
        || gradle.taskGraph.allTasks.any { it.path == ":nmcpPublish" }
        || gradle.taskGraph.allTasks
      .filterIsInstance<PublishToMavenRepository>()
      .any { task ->
        task.repository.name == "AdamkoDev"
      }
  })
}

//region Fix Gradle warning about signing tasks using publishing task outputs without explicit dependencies
// https://youtrack.jetbrains.com/issue/KT-46466 https://github.com/gradle/gradle/issues/26091
tasks.withType<AbstractPublishToMaven>().configureEach {
  val signingTasks = tasks.withType<Sign>()
  mustRunAfter(signingTasks)
}
//endregion


//region publishing logging
tasks.withType<AbstractPublishToMaven>().configureEach {
  val publicationGAV = provider { publication?.run { "$group:$artifactId:$version" } }
  doLast("log publication GAV") {
    if (publicationGAV.isPresent) {
      logger.info("[task: ${path}] ${publicationGAV.get()}")
    }
  }
}
//endregion

//region Maven Central can't handle parallel uploads, so limit parallel uploads with a service.
abstract class MavenPublishLimiter : BuildService<BuildServiceParameters.None>

val mavenPublishLimiter =
  gradle.sharedServices.registerIfAbsent("mavenPublishLimiter", MavenPublishLimiter::class) {
    maxParallelUsages = 1
  }

tasks.withType<PublishToMavenRepository>().configureEach {
  usesService(mavenPublishLimiter)
}
//endregion
