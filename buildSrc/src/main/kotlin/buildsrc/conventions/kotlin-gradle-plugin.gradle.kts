package buildsrc.conventions

plugins {
  id("buildsrc.conventions.base")
  id("buildsrc.conventions.java-base")
  id("org.gradle.kotlin.kotlin-dsl")

  id("buildsrc.conventions.maven-publishing")
  id("com.gradle.plugin-publish")
}

tasks.validatePlugins {
  enableStricterValidation = true
}

sourceSets {
  configureEach {
    java.setSrcDirs(emptyList<File>())
  }
}
