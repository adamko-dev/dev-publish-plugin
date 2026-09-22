package buildsrc.conventions

import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.JAR
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.attributes.Usage.JAVA_RUNTIME
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.gradle.kotlin.dsl.support.serviceOf


val bootstrapJars = configurations.dependencyScope("bootstrapJars") {
}

val bootstrapJarsResolver = configurations.resolvable(bootstrapJars.name + "Resolver") {
  extendsFrom(bootstrapJars)
  isTransitive = false
  attributes {
    attribute(USAGE_ATTRIBUTE, objects.named(JAVA_RUNTIME))
    attribute(CATEGORY_ATTRIBUTE, objects.named(LIBRARY))
    attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(JAR))
  }
}

tasks.register("updateBootstrap") {
  group = "build"
  description = "Rebuilds the jars in buildSrc/libs, which this build uses to build itself."

  val fs = serviceOf<FileSystemOperations>()

  val bootstrapLibsDir = layout.projectDirectory.dir("buildSrc/libs")
  outputs.dir(bootstrapLibsDir)

  val bootstrapJars: Provider<FileCollection> = bootstrapJarsResolver.map { it.incoming.files }
  inputs.files(bootstrapJars)
    .withNormalizer(ClasspathNormalizer::class)
    .ignoreEmptyDirectories()

  val readme = """
    |# Bootstrap jars
    |
    |This build uses the plugin it builds, so `buildSrc` needs DevPublish on its classpath before
    |DevPublish can be compiled. These jars break that cycle, and are committed for that reason.
    |
    |Do not edit them by hand. After changing the plugin, or the version in
    |`buildSrc/src/main/kotlin/buildsrc/conventions/maven-publishing.gradle.kts`, run:
    |
    |```shell
    |./gradlew updateBootstrap
    |```
    |
    |and commit the result.
    |""".trimMargin()
  inputs.property("readme", readme)

  doLast {
    fs.sync {
      from(bootstrapJars)
      into(bootstrapLibsDir)
    }
    bootstrapLibsDir.file("README.md").asFile.writeText(readme)
  }
}
