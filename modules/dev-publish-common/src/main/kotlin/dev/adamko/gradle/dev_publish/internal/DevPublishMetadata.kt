package dev.adamko.gradle.dev_publish.internal

import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.text.Charsets.UTF_8

/**
 * The metadata file that the DevPublish Gradle plugin writes, and `dev-publish-utils` reads back
 * inside a test JVM.
 */
@DevPublishInternalApi
object DevPublishMetadata {

  /** Name of the system property a build can set to pass the dev repo location to tests itself. */
  const val SYSTEM_PROPERTY_NAME: String = "devMavenRepo"

  /**
   * Path of the generated metadata file, relative to the root of the test runtime classpath.
   *
   * Put there by `dependencies { implementation(devPublish.dependency()) }`.
   */
  const val METADATA_FILE_PATH: String = "dev/adamko/gradle/dev_publish/dev-publish.properties"

  /**
   * Key holding the dev Maven repository location inside the metadata file.
   *
   * The value is a path **relative to the classpath root that contains the metadata file** - that
   * is, relative to the directory [METADATA_FILE_PATH] is resolved against. Recording it that way
   * means the file is byte-identical on every machine and checkout, so a test task consuming it
   * stays relocatable for the build cache, and the location can still be resolved without
   * depending on the test JVM's working directory.
   */
  const val DEV_MAVEN_REPO_KEY: String = "devMavenRepo"

  /** Written by hand, because [java.util.Properties.store] prefixes the file with a timestamp. */
  fun write(file: Path, devMavenRepo: String) {
    file.parent.createDirectories()
    file.writeText("$DEV_MAVEN_REPO_KEY=$devMavenRepo\n", UTF_8)
  }

  /** Read [DEV_MAVEN_REPO_KEY] from a metadata file, or `null` if it has no such entry. */
  fun read(input: InputStream): String? =
    input.bufferedReader()
      .lineSequence()
      .firstOrNull { it.startsWith("$DEV_MAVEN_REPO_KEY=") }
      ?.substringAfter("$DEV_MAVEN_REPO_KEY=")
}
