package buildsrc.conventions

import org.gradle.api.attributes.Bundling.BUNDLING_ATTRIBUTE
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.DocsType.DOCS_TYPE_ATTRIBUTE
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import provideDelegate
import registering

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

val javadocElements by configurations.consumable {
  attributes {
    attribute(USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    attribute(CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
    attribute(BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    attribute(DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.JAVADOC))
  }
  outgoing.artifact(javadocJar)
}

kotlin {
  @OptIn(ExperimentalKotlinGradlePluginApi::class)
  publishing.adhocSoftwareComponent {
    addVariantsFromConfiguration(javadocElements) {
      mapToOptional()
    }
  }
}

publishing {
  publications {
    register<MavenPublication>("mavenJava") {
      from(components["java"])
    }
  }
}
