package buildsrc.conventions

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
  id("buildsrc.conventions.base")
  id("buildsrc.conventions.java-base")
  id("org.jetbrains.kotlin.jvm")

  id("buildsrc.conventions.maven-publishing")
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

val javadocJar by tasks.registering(Jar::class) {
  archiveClassifier.set("javadoc")
  from(tasks.named("createJavadocJarReadme"))
}

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])
      artifact(javadocJar)
    }
  }
}
