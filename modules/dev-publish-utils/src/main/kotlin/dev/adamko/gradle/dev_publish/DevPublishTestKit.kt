@file:JvmName("DevPublishTestKit")

package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.internal.DevPublishMetadata
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*


/** Name of the repository declared in the generated scripts. */
private const val REPO_NAME = "DevMavenRepo"

/**
 * The location of the dev Maven repository.
 *
 * Declare `dependencies { implementation(devPublish.dependency()) }` in the build script, and this
 * reads the location from the metadata file that declaration puts on the runtime classpath.
 *
 * @param[systemPropertyName] Fallback for builds that pass the location as a system property themselves.
 */
@JvmOverloads
fun devMavenRepo(
  systemPropertyName: String = DevPublishMetadata.SYSTEM_PROPERTY_NAME,
): Path {
  val fromClasspath = devMavenRepoFromClasspath()
  if (fromClasspath != null) return fromClasspath

  val fromSystemProperty = System.getProperty(systemPropertyName)
  if (fromSystemProperty != null) return Path(fromSystemProperty)

  error(
    "The dev Maven repository could not be located. " +
        "Add it to this test's runtime classpath with " +
        "`dependencies { implementation(devPublish.dependency()) }`."
  )
}

/**
 * Read the dev Maven repository location from the metadata file that
 * `dependencies { implementation(devPublish.dependency()) }` puts on the runtime classpath.
 *
 * Returns `null` when there is no metadata file, so the caller can fall back to the system property.
 *
 * @see DevPublishMetadata.DEV_MAVEN_REPO_KEY
 */
private fun devMavenRepoFromClasspath(): Path? {
  val metadataUrl = ClasspathAnchor::class.java
    .getResource("/${DevPublishMetadata.METADATA_FILE_PATH}")
    ?: return null

  val recorded = metadataUrl.openStream().use(DevPublishMetadata::read) ?: return null

  val classpathRoot = metadataUrl.classpathRoot()
    ?: error(
      "The dev Maven repository metadata was found at $metadataUrl, which is not a file on disk, " +
          "so the recorded location '$recorded' cannot be resolved against it. DevPublish expects " +
          "the metadata to be a directory on the runtime classpath."
    )

  val path = classpathRoot.resolve(recorded).normalize()

  if (!path.exists()) {
    error(
      "The dev Maven repository was recorded as '$recorded', relative to " +
          "${classpathRoot.invariantSeparatorsPathString}, but nothing exists at " +
          "${path.invariantSeparatorsPathString}."
    )
  }

  return path
}

/**
 * The classpath root that [DevPublishMetadata.METADATA_FILE_PATH] was resolved against, or `null`
 * when the metadata is not a plain file (for example, when it has been packed into a jar).
 */
private fun URL.classpathRoot(): Path? {
  if (protocol != "file") return null
  var dir: Path = Paths.get(toURI())
  repeat(DevPublishMetadata.METADATA_FILE_PATH.split("/").size) {
    dir = dir.parent ?: return null
  }
  return dir
}

/** Anchor for loading the metadata file from this class loader. */
private object ClasspathAnchor

/**
 * An `exclusiveContent { }` block for the dev Maven repository, to be placed inside a
 * `repositories { }` block.
 *
 * The groups are derived from the publications actually present in [devMavenRepo], so a Gradle
 * plugin's Plugin Marker Artifact and its implementation are both covered without either having to
 * be named.
 */
@JvmOverloads
fun devPublishRepository(
  devMavenRepo: Path = devMavenRepo(),
): String {
  val groups = publishedGroups(devMavenRepo)

  if (groups.isEmpty()) {
    error(
      "No publications found in the dev Maven repository " +
          "${devMavenRepo.invariantSeparatorsPathString}. " +
          "Did the test task depend on `updateDevRepo`? " +
          "Declaring `implementation(devPublish.dependency())` wires that up."
    )
  }

  return buildString {
    appendLine("exclusiveContent {")
    appendLine("  forRepository {")
    appendLine("""    maven(file("${devMavenRepo.scriptPath()}")) {""")
    appendLine("""      name = "$REPO_NAME"""")
    appendLine("    }")
    appendLine("  }")
    appendLine("  filter {")
    groups.forEach { group ->
      appendLine("""    includeGroup("$group")""")
    }
    appendLine("  }")
    append("}")
  }
}

/**
 * A complete `pluginManagement { }` block, so that the build under test can apply plugins published
 * to the dev Maven repository by plugin id and version.
 */
@JvmOverloads
fun devPublishPluginManagement(
  devMavenRepo: Path = devMavenRepo(),
  gradlePluginPortal: Boolean = true,
  mavenCentral: Boolean = true,
): String = buildString {
  appendLine("pluginManagement {")
  appendLine("  repositories {")
  appendLine(devPublishRepository(devMavenRepo).prependIndent("    "))
  if (gradlePluginPortal) appendLine("    gradlePluginPortal()")
  if (mavenCentral) appendLine("    mavenCentral()")
  appendLine("  }")
  append("}")
}

/**
 * A complete `dependencyResolutionManagement { }` block, so that the build under test can resolve
 * ordinary dependencies published to the dev Maven repository.
 */
@JvmOverloads
fun devPublishDependencyResolutionManagement(
  devMavenRepo: Path = devMavenRepo(),
  mavenCentral: Boolean = true,
): String = buildString {
  appendLine("dependencyResolutionManagement {")
  appendLine("  repositories {")
  appendLine(devPublishRepository(devMavenRepo).prependIndent("    "))
  if (mavenCentral) appendLine("    mavenCentral()")
  appendLine("  }")
  append("}")
}

/**
 * A complete `settings.gradle.kts` for a build under test: both
 * [devPublishPluginManagement] and [devPublishDependencyResolutionManagement], and optionally
 * `rootProject.name`.
 *
 * ```
 * projectDir.resolve("settings.gradle.kts")
 *   .writeText(devPublishSettings(rootProjectName = "my-test-project"))
 * ```
 */
@JvmOverloads
fun devPublishSettings(
  rootProjectName: String? = null,
  devMavenRepo: Path = devMavenRepo(),
): String = buildString {
  appendLine(devPublishPluginManagement(devMavenRepo))
  appendLine()
  appendLine(devPublishDependencyResolutionManagement(devMavenRepo))
  if (rootProjectName != null) {
    appendLine()
    append("""rootProject.name = "$rootProjectName"""")
  }
}

/**
 * Every Maven group with a publication in [devMavenRepo].
 *
 * A Maven repository is laid out as `<group as directories>/<artifactId>/<version>/<files>`, so the
 * group of each `.pom` is its path with the last three segments removed.
 */
internal fun publishedGroups(devMavenRepo: Path): Set<String> =
  devMavenRepo
    .walk()
    .filter { it.isRegularFile() && it.extension == "pom" }
    .mapNotNull { pom ->
      val segments = pom.relativeTo(devMavenRepo).invariantSeparatorsPathString.split("/")
      // <group...>, <artifactId>, <version>, <fileName>
      if (segments.size < 4) null else segments.dropLast(3).joinToString(".")
    }
    .toSortedSet()

/** Resolve symlinks, and use `/` separators, so the path is valid inside a Kotlin script. */
private fun Path.scriptPath(): String =
  toFile().canonicalFile.invariantSeparatorsPath.escapeForKotlinString()

private fun String.escapeForKotlinString(): String =
  replace("""\""", """\\""")
    .replace("\"", """\"""")
    .replace("$", "\${'$'}")
