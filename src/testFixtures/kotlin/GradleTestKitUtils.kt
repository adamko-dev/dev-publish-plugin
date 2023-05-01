package dev.adamko.gradle.dev_publish.test_utils

import dev.adamko.gradle.dev_publish.test_utils.GradleProjectTest.Companion.testMavenRepoPathString
import org.gradle.testkit.runner.GradleRunner
import org.intellij.lang.annotations.Language
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.name
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


// utils for testing using Gradle TestKit


class GradleProjectTest(
  override val projectDir: Path,
) : ProjectDirectoryScope {

  constructor(
    testProjectName: String,
    baseDir: Path = funcTestTempDir,
  ) : this(projectDir = baseDir.resolve(testProjectName))

  val runner: GradleRunner = GradleRunner.create()
    .withProjectDir(projectDir.toFile())

  val projectName by projectDir::name

  companion object {

    /** file-based Maven Repo that contains the published plugin */
    private val testMavenRepoDir: Path by systemProperty(Paths::get)

    val testMavenRepoPathString
      get() = testMavenRepoDir
        .toFile()
        .canonicalFile
        .absoluteFile
        .invariantSeparatorsPath

    val projectTestTempDir: Path by systemProperty(Paths::get)

    /** Temporary directory for the functional tests */
    val funcTestTempDir: Path by lazy {
      projectTestTempDir.resolve("functional-tests")
    }
  }
}


/**
 * Builder for testing a Gradle project that uses Kotlin script DSL and creates default
 * `settings.gradle.kts` and `gradle.properties` files.
 *
 * @param[testProjectName] the path of the project directory
 */
fun gradleKtsProjectTest(
  testProjectName: String,
  baseDir: Path = GradleProjectTest.funcTestTempDir,
  build: GradleProjectTest.() -> Unit,
): GradleProjectTest {
  return GradleProjectTest(baseDir = baseDir, testProjectName = testProjectName).apply {

    settingsGradleKts = """
      |rootProject.name = "$projectName"
      |
      |pluginManagement {
      |  repositories {
      |    maven(file("$testMavenRepoPathString")) {
      |      mavenContent {
      |        includeGroup("dev.adamko.dev-publish")
      |        includeGroup("dev.adamko.gradle")
      |      }
      |    }
      |    mavenCentral()
      |    gradlePluginPortal()
      |  }
      |}
      |
      |dependencyResolutionManagement {
      |  repositories {
      |    mavenCentral()
      |  }
      |}
      |
    """.trimMargin()

    buildGradleKts = """
      |
    """.trimMargin()

    gradleProperties = """
      |
    """.trimMargin()

    build()
  }
}

fun GradleProjectTest.projectFile(
  @Language("TEXT")
  filePath: String
): PropertyDelegateProvider<Any?, ReadWriteProperty<Any?, String>> =
  PropertyDelegateProvider { _, _ ->
    TestProjectFileProvidedDelegate(this, filePath)
  }


/** Delegate for reading and writing a [GradleProjectTest] file. */
private class TestProjectFileProvidedDelegate(
  private val project: GradleProjectTest,
  private val filePath: String,
) : ReadWriteProperty<Any?, String> {
  override fun getValue(thisRef: Any?, property: KProperty<*>): String =
    project.projectDir.resolve(filePath).toFile().readText()

  override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
    project.createFile(filePath, value)
  }
}

/** Delegate for reading and writing a [GradleProjectTest] file. */
class TestProjectFileDelegate(
  private val filePath: String,
) : ReadWriteProperty<ProjectDirectoryScope, String> {
  override fun getValue(thisRef: ProjectDirectoryScope, property: KProperty<*>): String =
    thisRef.projectDir.resolve(filePath).toFile().readText()

  override fun setValue(thisRef: ProjectDirectoryScope, property: KProperty<*>, value: String) {
    thisRef.createFile(filePath, value)
  }
}


@DslMarker
annotation class ProjectDirectoryDsl

@ProjectDirectoryDsl
interface ProjectDirectoryScope {
  val projectDir: Path
}

private data class ProjectDirectoryScopeImpl(
  override val projectDir: Path
) : ProjectDirectoryScope


fun ProjectDirectoryScope.createFile(filePath: String, contents: String): File =
  projectDir.resolve(filePath).toFile().apply {
    parentFile.mkdirs()
    createNewFile()
    writeText(contents)
  }


@ProjectDirectoryDsl
fun ProjectDirectoryScope.dir(
  path: String,
  block: ProjectDirectoryScope.() -> Unit = {},
): ProjectDirectoryScope =
  ProjectDirectoryScopeImpl(projectDir.resolve(path)).apply(block)


@ProjectDirectoryDsl
fun ProjectDirectoryScope.file(
  path: String
): Path = projectDir.resolve(path)


/** Read or write the `settings.gradle.kts` file contents in the current directory */
@delegate:Language("kts")
var ProjectDirectoryScope.settingsGradleKts: String by TestProjectFileDelegate("settings.gradle.kts")


/** Read or write the `build.gradle.kts` file contents in the current directory */
@delegate:Language("kts")
var ProjectDirectoryScope.buildGradleKts: String by TestProjectFileDelegate("build.gradle.kts")


/** Read or write the `gradle.properties` file contents in the current directory */
@delegate:Language("properties")
var ProjectDirectoryScope.gradleProperties: String by TestProjectFileDelegate("gradle.properties")


fun ProjectDirectoryScope.createKotlinFile(filePath: String, @Language("kotlin") contents: String) =
  createFile(filePath, contents)


fun ProjectDirectoryScope.createKtsFile(filePath: String, @Language("kts") contents: String) =
  createFile(filePath, contents)
