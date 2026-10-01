package buildsrc.conventions

import org.gradle.api.JavaVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.`java-base`

plugins {
  id("buildsrc.conventions.base")
  `java`
}

extensions.getByType<JavaPluginExtension>().apply {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
  }
  withSourcesJar()
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

val createJavadocJarReadme by tasks.registering(Sync::class) {
  description = "generate a readme.txt for the Javadoc JAR"
  from(
    resources.text.fromString(
      """
      |This Javadoc JAR is intentionally empty.
      |
      |For documentation, see:
      |* https://github.com/adamko-dev/dev-publish-plugin
      |* Or the sources JAR. 
      |""".trimMargin()
    )
  ) {
    rename { "readme.txt" }
  }
  into(temporaryDir)
}
