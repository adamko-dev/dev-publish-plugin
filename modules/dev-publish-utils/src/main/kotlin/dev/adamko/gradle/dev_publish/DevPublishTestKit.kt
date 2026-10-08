@file:JvmName("DevPublishTestKit")

package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.internal.DevPublishMetadata.REPO_LOCATION_FILE_NAME
import java.io.BufferedReader
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.text.Charsets.UTF_8

/** Name of the repository declared in the generated scripts. */
private const val REPO_NAME = "DevMavenRepo"

/** Script language of the generated scripts. */
sealed class ScriptDsl {
  companion object {
    @JvmField
    val Kotlin: ScriptDsl = KotlinInternal
    @JvmField
    val Groovy: ScriptDsl = GroovyInternal
  }

  internal data object KotlinInternal : ScriptDsl()
  internal data object GroovyInternal : ScriptDsl()
}

/**
 * The location of the dev Maven repository.
 *
 * Declare `dependencies { implementation(devPublish.dependency()) }` in the build script, and this
 * reads the location from the metadata file that declaration puts on the runtime classpath.
 */
fun devMavenRepo(): Path {
  return when (val fromClasspath = devMavenRepoFromClasspath()) {
    is DevRepoFromClasspathResult.DevMavenRepo -> fromClasspath.path
    is DevRepoFromClasspathResult.Failure      -> error(
      "The dev Maven repository could not be located. " + fromClasspath.message
    )
  }
}

/**
 * Read the dev Maven repository location from the metadata file that
 * `dependencies { implementation(devPublish.dependency()) }` puts on the runtime classpath.
 *
 * Must also discover the location of the metadata file on the runtime classpath,
 * because the dev-maven repo is a relative path relative to the metadata file.
 */
private fun devMavenRepoFromClasspath(): DevRepoFromClasspathResult {
  val resourcePath = "/$REPO_LOCATION_FILE_NAME"

  val addTheDependency =
    "Add it to this test's runtime classpath with " +
        "`dependencies { implementation(devPublish.dependency()) }`."

  val devPublishRelativeLocation =
    {}::class.java.getResourceAsStream(resourcePath)
      ?.bufferedReader(UTF_8)
      ?.use(BufferedReader::readText)
      ?: return DevRepoFromClasspathResult.Failure(
        "getResourceAsStream could not find $resourcePath in the runtime classpath. $addTheDependency"
      )

  val devPublishActualLocation =
    {}::class.java.getResource(resourcePath)
      ?: return DevRepoFromClasspathResult.Failure(
        "getResource could not find $resourcePath in the runtime classpath. $addTheDependency"
      )

  if (devPublishActualLocation.protocol != "file")
    return DevRepoFromClasspathResult.Failure(
      "The dev Maven repository metadata was found at $devPublishActualLocation, which is not a file on disk, " +
          "so the recorded location '$devPublishRelativeLocation' cannot be resolved against it. " +
          "Dev Publish expects the metadata to be a directory on the runtime classpath."
    )

  val metadataDir = devPublishActualLocation.toURI().toPath().parent

  val devMavenRepo = metadataDir
    .resolve(devPublishRelativeLocation)
    .normalize()

  // the recorded path is relative, so a stale or misconfigured one resolves to a plausible-looking
  // directory that simply is not there - report that here, not later as a missing artifact
  if (!devMavenRepo.exists()) {
    return DevRepoFromClasspathResult.Failure(
      "The location was recorded as '$devPublishRelativeLocation', relative to " +
          "${metadataDir.invariantSeparatorsPathString}, but nothing exists at " +
          "${devMavenRepo.invariantSeparatorsPathString}. " +
          "Run the `updateDevRepo` task to create it."
    )
  }

  return DevRepoFromClasspathResult.DevMavenRepo(devMavenRepo)
}

private sealed interface DevRepoFromClasspathResult {
  @JvmInline
  value class DevMavenRepo(val path: Path) : DevRepoFromClasspathResult
  @JvmInline
  value class Failure(val message: String) : DevRepoFromClasspathResult
}

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
  scriptDsl: ScriptDsl = ScriptDsl.Kotlin,
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

  val repoUrl = devMavenRepo.invariantSeparatorsPathString.asQuotedString(scriptDsl)
  val repoName = REPO_NAME.asQuotedString(scriptDsl)

  return buildString {
    appendLine("exclusiveContent {")
    appendLine("  forRepository {")
    when (scriptDsl) {
      ScriptDsl.KotlinInternal -> {
        appendLine("    maven(file($repoUrl)) {")
        appendLine("      name = $repoName")
      }

      ScriptDsl.GroovyInternal -> {
        appendLine("    maven {")
        appendLine("      url = file($repoUrl)")
        appendLine("      name = $repoName")
      }
    }
    appendLine("    }")
    appendLine("  }")
    appendLine("  filter {")
    groups.sorted().forEach { group ->
      appendLine("    includeGroup(${group.asQuotedString(scriptDsl)})")
    }
    appendLine("  }")
    append("}")
    appendLine()
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
  scriptDsl: ScriptDsl = ScriptDsl.Kotlin,
): String = buildString {
  appendLine("pluginManagement {")
  appendLine("  repositories {")
  appendLine(devPublishRepository(devMavenRepo, scriptDsl).trim().prependIndent("    "))
  if (gradlePluginPortal) appendLine("    gradlePluginPortal()")
  if (mavenCentral) appendLine("    mavenCentral()")
  appendLine("  }")
  append("}")
  appendLine()
}

/**
 * A complete `dependencyResolutionManagement { }` block, so that the build under test can resolve
 * ordinary dependencies published to the dev Maven repository.
 */
@JvmOverloads
fun devPublishDependencyResolutionManagement(
  devMavenRepo: Path = devMavenRepo(),
  mavenCentral: Boolean = true,
  scriptDsl: ScriptDsl = ScriptDsl.Kotlin,
): String = buildString {
  appendLine("dependencyResolutionManagement {")
  appendLine("  repositories {")
  appendLine(devPublishRepository(devMavenRepo, scriptDsl).trim().prependIndent("    "))
  if (mavenCentral) appendLine("    mavenCentral()")
  appendLine("  }")
  append("}")
  appendLine()
}

/**
 * A complete settings script for a build under test: both
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
  scriptDsl: ScriptDsl = ScriptDsl.Kotlin,
): String = buildString {
  appendLine(devPublishPluginManagement(devMavenRepo, scriptDsl = scriptDsl))
  appendLine()
  appendLine(devPublishDependencyResolutionManagement(devMavenRepo, scriptDsl = scriptDsl))
  if (rootProjectName != null) {
    appendLine()
    append("rootProject.name = ${rootProjectName.asQuotedString(scriptDsl)}")
  }
}

/**
 * Every Maven group with a publication in [devMavenRepo].
 *
 * A Maven repository is laid out as `<group as directories>/<artifactId>/<version>/<files>`, so the
 * group of each `.pom` is its path with the last three segments removed.
 */
private fun publishedGroups(devMavenRepo: Path): Set<String> =
  devMavenRepo
    .walk()
    .filter { it.isRegularFile() && it.extension == "pom" }
    .mapNotNull { pom ->
      val segments = pom.relativeTo(devMavenRepo).map(Path::name)
      // <group...>, <artifactId>, <version>, <fileName>
      if (segments.size < 4) {
        null
      } else {
        segments.dropLast(3).joinToString(".")
      }
    }
    .toSortedSet()

/** Quote the string as a string literal, escaped for [ScriptDsl]. */
private fun String.asQuotedString(dsl: ScriptDsl): String {
  val escaped = replace("""\""", """\\""")
  return when (dsl) {
    ScriptDsl.KotlinInternal -> """"${escaped.replace("\"", """\"""").replace("$", $$"${'$'}")}""""
    ScriptDsl.GroovyInternal -> "'${escaped.replace("'", """\'""")}'"
  }
}
