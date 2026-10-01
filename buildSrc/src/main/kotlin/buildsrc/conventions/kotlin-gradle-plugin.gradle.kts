package buildsrc.conventions

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
  id("buildsrc.conventions.base")
  id("buildsrc.conventions.java-base")
  id("org.gradle.kotlin.kotlin-dsl")

  id("buildsrc.conventions.maven-publishing")
  id("com.gradle.plugin-publish")
}

kotlin {
  jvmToolchain(17)
  @OptIn(ExperimentalAbiValidation::class)
  abiValidation {}
  compilerOptions {
    optIn.addAll(
      "kotlin.io.path.ExperimentalPathApi",
    )
  }
}

tasks.validatePlugins {
  enableStricterValidation = true
}

sourceSets {
  configureEach {
    java.setSrcDirs(emptyList<File>())
  }
}

// The Gradle Publish Plugin enables the Javadoc JAR in afterEvaluate, so find it lazily
tasks.withType<Jar>()
  .matching { it.name == "javadocJar" }
  .configureEach {
    from(tasks.named("createJavadocJarReadme"))
  }
