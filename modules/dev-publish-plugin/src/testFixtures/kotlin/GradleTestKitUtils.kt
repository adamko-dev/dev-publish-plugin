package dev.adamko.gradle.dev_publish.test_utils

import dev.adamko.gradle.dev_publish.devMavenRepo
import dev.adamko.gradle.dev_publish.test_utils.GradleProjectTest.Companion.settingRepositories
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import org.intellij.lang.annotations.Language


// utils for testing using Gradle TestKit


class GradleProjectTest(
  override val projectDir: Path,
  val projectName: String = projectDir.name.lowercase(),
) : ProjectDirectoryScope {

  constructor(
    testProjectPath: String,
    baseDir: Path = funcTestTempDir,
    projectName: String,
  ) : this(
    projectDir = baseDir.resolve(testedGradleVersion.version).resolve(testProjectPath),
    projectName = projectName,
  )

  val runner: GradleRunner = GradleRunner.create()
    .withProjectDir(projectDir.toFile())
    .withGradleVersion(testedGradleVersion.version)
    .withReadOnlyDependencyCache()

  companion object {

    val testedGradleVersion: GradleVersion by systemProperty(GradleVersion::version)

    /** file-based Maven Repo that contains the published plugin */
    private val testMavenRepoDir: Path by lazy { devMavenRepo() }

    val testMavenRepoPathString: String
      get() = testMavenRepoDir
        .absolute().normalize().invariantSeparatorsPathString

    val settingRepositories: String
      get() {
        val devMavenRepo = """
              |exclusiveContent {
              |  forRepository {
              |    maven(file("$testMavenRepoPathString")) {
              |      name = "MavenDevRepo"
              |    }
              |  }
              |  filter { 
              |    includeGroup("dev.adamko.dev-publish")
              |    includeGroup("dev.adamko.gradle")
              |  }
              |}
              |""".trimMargin().prependIndent("    ")
        return """
              |pluginManagement {
              |  repositories {
              |${devMavenRepo}
              |    mavenCentral()
              |    gradlePluginPortal()
              |  }
              |}
              |
              |dependencyResolutionManagement {
              |  repositories {
              |${devMavenRepo}
              |    mavenCentral()
              |  }
              |}
              """.trimMargin()
      }

    val projectTestTempDir: Path by systemProperty(Paths::get)

    /** The DevPublish version being built, e.g. `1.2.0-SNAPSHOT`. */
    val devPublishVersion: String by systemProperty()

    /** Temporary directory for the functional tests */
    val funcTestTempDir: Path by lazy {
      projectTestTempDir.resolve("functional-tests")
    }

    /**
     * Gradle User Home of the current machine. Defaults to `~/.gradle`, but might be different on CI.
     *
     * This value is provided by the Gradle Test task.
     */
    private val hostGradleUserHome: Path? by optionalSystemProperty(Paths::get)

    /**
     * Gradle dependencies cache of the current machine.
     * Used as a read-only dependencies cache by setting `GRADLE_RO_DEP_CACHE`
     *
     * See https://docs.gradle.org/9.8.0/userguide/dependency_caching.html#sec:shared-readonly-cache
     */
    internal val hostGradleDependenciesCache: Path? by lazy {
      hostGradleUserHome?.resolve("caches")
    }

    internal fun GradleRunner.withReadOnlyDependencyCache(): GradleRunner {
      val cacheDir = hostGradleDependenciesCache?.takeIf { it.exists() }
        ?: return this

      return withEnvironment(
        buildMap {
          // `withEnvironment()` will wipe all existing environment variables,
          // which breaks things like ANDROID_HOME and PATH, so re-add them.
          putAll(System.getenv())

          if (cacheDir.exists()) {
            put("GRADLE_RO_DEP_CACHE", cacheDir.invariantSeparatorsPathString)
          }
        }
      )
    }
  }
}


/**
 * Builder for testing a Gradle project that uses Kotlin script DSL and creates default
 * `settings.gradle.kts` and `gradle.properties` files.
 *
 * @param[testProjectPath] the path of the project directory, relative to [baseDir].
 */
fun gradleKtsProjectTest(
  projectName: String,
  testProjectPath: String,
  baseDir: Path = GradleProjectTest.funcTestTempDir,
  build: GradleProjectTest.() -> Unit,
): GradleProjectTest {
  return GradleProjectTest(
    baseDir = baseDir,
    testProjectPath = testProjectPath,
    projectName = projectName,
  ).apply {

    settingsGradleKts = """
      |rootProject.name = "${this.projectName}"
      |
      |$settingRepositories
      |""".trimMargin()

    buildGradleKts = ""

    gradleProperties = """
      |org.gradle.jvmargs=-Dfile.encoding=UTF-8
      |org.gradle.caching=true
      |org.gradle.configuration-cache=true
      |org.gradle.logging.stacktrace=all
      |org.gradle.parallel=true
      |org.gradle.welcome=never
      |org.gradle.isolated-projects=true
      |""".trimMargin()

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
    project.projectDir.resolve(filePath).readText()

  override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
    project.createFile(filePath, value)
  }
}

/** Delegate for reading and writing a [GradleProjectTest] file. */
class TestProjectFileDelegate(
  private val filePath: String,
) : ReadWriteProperty<ProjectDirectoryScope, String> {
  override fun getValue(thisRef: ProjectDirectoryScope, property: KProperty<*>): String =
    thisRef.projectDir.resolve(filePath).readText()

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


fun ProjectDirectoryScope.createFile(filePath: String, contents: String): Path =
  projectDir.resolve(filePath).apply {
    parent.createDirectories()
    writeText(contents)
  }


fun ProjectDirectoryScope.dir(
  path: String,
  block: ProjectDirectoryScope.() -> Unit = {},
): ProjectDirectoryScope =
  ProjectDirectoryScopeImpl(projectDir.resolve(path)).apply(block)


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

fun ProjectDirectoryScope.createJavaFile(filePath: String, @Language("java") contents: String) =
  createFile(filePath, contents)
