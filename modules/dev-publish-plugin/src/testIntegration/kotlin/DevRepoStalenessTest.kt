package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.DevRepoStalenessTest.Companion.libBuildGradleKts
import dev.adamko.gradle.dev_publish.test_utils.*
import dev.adamko.gradle.dev_publish.test_utils.GradleProjectTest.Companion.testedGradleVersion
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestCaseOrder
import io.kotest.core.test.TestScope
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.io.path.*
import org.gradle.testkit.runner.TaskOutcome.*

/**
 * A consumer's test task must re-run when the dev repo contents change.
 *
 * Every test asserts two things: that the change really did reach the dev repo, and that the
 * consumer's test task noticed. Splitting them apart tells a publishing failure
 * (`PublishToMavenRepository` was skipped) from an invalidation failure (the test task stayed
 * up-to-date against a repo that had moved on).
 *
 * One context per project shape, because the shape decides which published files a change lands
 * in. Within a context the tests run in order and depend on the ones before them - some exist
 * only to set up or revert state for the next.
 */
class DevRepoStalenessTest : FunSpec({

  context("a consumer of a Kotlin library") {
    val project = libraryProject()

    test("1st run - the repo is populated, and the tests run") {
      project.runner.withArguments(":tests:test").build {
        project.repoFile("com/example/lib/1.0.0/lib-1.0.0.jar").shouldExist()

        shouldHaveTaskWithOutcome(":tests:test", SUCCESS)
      }
    }

    test("expect an unchanged re-run does not re-run the tests") {
      project.runner.withArguments(":tests:test").build {
        shouldHaveTaskWithOutcome(":lib:publishMavenJavaPublicationToDevPublishMavenRepository", UP_TO_DATE)
        shouldHaveTaskWithOutcome(":tests:test", UP_TO_DATE)
      }
    }

    test("expect adding a class re-runs the consumer's tests") {
      project.dir("lib").createKotlinFile(
        "src/main/kotlin/com/example/lib/LibClass2.kt",
        """
          |package com.example.lib
          |
          |class LibClass2
          |""".trimMargin()
      )

      project.runner.withArguments(":tests:test").build {
        project.repoJarEntries("com/example/lib/1.0.0/lib-1.0.0.jar") shouldContain
            "com/example/lib/LibClass2.class"

        shouldHaveTaskWithOutcome(":tests:test", SUCCESS)
      }
    }

    // the fingerprint is derived from contents only, so reverting returns it to an earlier value -
    // proving it reaches the build cache key, not just the up-to-date check
    test("expect removing the class again restores the cached result") {
      project.file("lib/src/main/kotlin/com/example/lib/LibClass2.kt").deleteExisting()

      project.runner.withArguments(":tests:test").build {
        shouldHaveTaskWithOutcome(":tests:test", FROM_CACHE)
      }
    }
  }

  context("a consumer of a project with a non-default publication") {
    val project = customPublicationProject()
    val versionDir = "com/example/renamed-lib/1.0.0"

    test("1st run - sources, javadoc and both extra artifacts are all published") {
      project.runner
        .withArguments(":tests:test")
        .build {
          project.repoFile("$versionDir/renamed-lib-1.0.0-sources.jar").shouldExist()
          project.repoFile("$versionDir/renamed-lib-1.0.0-javadoc.jar").shouldExist()

          // published directly, so it is in the repo but not in the metadata
          project.repoFile("$versionDir/renamed-lib-1.0.0-extra.txt").readText().trim() shouldBe "v1"

          // published through the software component, so it is in the metadata too
          project.repoFile("$versionDir/renamed-lib-1.0.0-tracked.jar").shouldExist()
          project.file("lib/build/publications/customPub/module.json")
            .readText() shouldContain "renamed-lib-1.0.0-tracked.jar"

          shouldHaveTaskWithOutcome(":tests:test", SUCCESS)
        }
    }

    // no source file changes, so every compile task stays up-to-date and only the jar differs
    test("expect a change to src/main/resources re-runs the consumer's tests") {
      project.file("lib/src/main/resources/extra.txt").apply {
        parent.createDirectories()
        writeText("added\n")
      }

      project.runner.withArguments(":tests:test").build {
        project.repoJarEntries("$versionDir/renamed-lib-1.0.0.jar") shouldContain "extra.txt"

        shouldHaveTaskWithOutcome(":tests:test", SUCCESS)
      }
    }

    // `artifact(...)` is in neither the POM nor Gradle Module Metadata, but it is one of the
    // publish task's `publishableFiles` inputs.
    //
    // Must run before the next test, otherwise that test's re-publish would sweep the change in.
    test("expect a change to a directly-declared artifact re-runs the consumer's tests") {
      project.file("lib/src/extra/payload.txt").writeText("v2\n")

      project.runner.withArguments(":tests:test").build {
        project.repoFile("$versionDir/renamed-lib-1.0.0-extra.txt").readText().trim() shouldBe "v2"

        shouldHaveTaskWithOutcome(":tests:test", SUCCESS)
      }
    }

    test("expect a change to an artifact published via the SoftwareComponent re-runs the consumer's tests") {
      project.file("lib/src/tracked/payload.txt").writeText("v2\n")

      project.runner.withArguments(":tests:test").build {
        project.repoJarEntryText("$versionDir/renamed-lib-1.0.0-tracked.jar", "payload.txt")
          .trim() shouldBe "v2"

        shouldHaveTaskWithOutcome(":tests:test", SUCCESS)
      }
    }

    // dependencies are recorded in Gradle Module Metadata, so this one should be caught
    test("expect adding a dependency re-runs the consumer's tests") {
      project.dir("lib").buildGradleKts = libBuildGradleKts(dependOnDep = true)

      project.runner.withArguments(":tests:test").build {
        project.repoFile("$versionDir/renamed-lib-1.0.0.pom").readText() shouldContain
            "<artifactId>dep</artifactId>"

        shouldHaveTaskWithOutcome(":tests:test", SUCCESS)
      }
    }

    // the POM description isn't in Gradle Module Metadata
    test("expect a change to the POM only re-runs the consumer's tests") {
      project.dir("lib").buildGradleKts =
        libBuildGradleKts(dependOnDep = true, pomDescription = "CHANGED description")

      project.runner.withArguments(":tests:test").build {
        project.repoFile("$versionDir/renamed-lib-1.0.0.pom").readText() shouldContain "CHANGED"

        shouldHaveTaskWithOutcome(":tests:test", SUCCESS)
      }
    }
  }

  context("a consumer of a relocated publication") {
    val project = relocationProject()
    val relocationPom = "com/example/old-name/1.0.0/old-name-1.0.0.pom"

    test("1st run - the relocation POM is published") {
      project.runner.withArguments(":tests:test").build {
        project.repoFile(relocationPom).readText() shouldContain "<artifactId>renamed-lib</artifactId>"

        shouldHaveTaskWithOutcome(":tests:test", SUCCESS)
      }
    }

    // a relocation publication is POM-only - it has no software component, so no Gradle Module
    // Metadata.
    //
    // Same shape as the Plugin Marker Artifact test below. Both are kept: the marker is the
    // common case, and relocation is a documented Gradle API that reaches it a different way.
    test("expect a change to the relocation target re-runs the consumer's tests") {
      project.dir("lib").buildGradleKts = relocationBuildGradleKts(relocateTo = "moved-again")

      project.runner.withArguments(":tests:test").build {
        project.repoFile(relocationPom).readText() shouldContain "<artifactId>moved-again</artifactId>"

        shouldHaveTaskWithOutcome(":tests:test", SUCCESS)
      }
    }
  }

  context("a consumer of a publication that pins resolved dependency versions") {
    val project = versionMappingProject()
    val libPom = "com/example/lib/1.0.0/lib-1.0.0.pom"

    test("1st run - the dynamic version is published as the version it resolved to") {
      project.runner.withArguments(":tests:test").build {
        project.repoFile(libPom).readText() shouldContain "<version>1.0</version>"

        shouldHaveTaskWithOutcome(":tests:test", SUCCESS)
      }
    }

    // nothing in the project changes here - a new version appears upstream, `1.+` resolves to it,
    // and `fromResolutionResult()` bakes the new version into the published POM
    test("expect a new upstream version re-runs the consumer's tests") {
      project.publishUpstream("1.0", "1.1")

      project.runner.withArguments(":tests:test").build {
        project.repoFile(libPom).readText() shouldContain "<version>1.1</version>"

        shouldHaveTaskWithOutcome(":tests:test", SUCCESS)
      }
    }
  }

  context("a consumer of a Gradle plugin project") {
    val project = gradlePluginProject()
    val markerPom =
      "com/example/greeting/com.example.greeting.gradle.plugin/1.0.0/" +
          "com.example.greeting.gradle.plugin-1.0.0.pom"

    test("1st run - the Plugin Marker Artifact is published") {
      project.runner.withArguments(":tests:test").build {
        project.repoFile(markerPom).readText() shouldContain "<name>Greeting plugin</name>"

        shouldHaveTaskWithOutcome(":tests:test", SUCCESS)
      }
    }

    // `displayName` lands in the Plugin Marker Artifact's POM and nowhere else. A marker
    // publication has no Gradle Module Metadata.
    test("expect a change to only the Plugin Marker Artifact re-runs the consumer's tests") {
      project.dir("plugin").buildGradleKts = pluginBuildGradleKts(displayName = "RENAMED plugin")

      project.runner.withArguments(":tests:test").build {
        project.repoFile(markerPom).readText() shouldContain "<name>RENAMED plugin</name>"

        shouldHaveTaskWithOutcome(":tests:test", SUCCESS)
      }
    }
  }

  // A SNAPSHOT re-publish writes new timestamps into file names and `maven-metadata.xml`, even when
  // nothing changed. Re-publishing is forced here by deleting `lib/build`, which holds the
  // publication store.
  context("a consumer of a SNAPSHOT publication") {
    val project = snapshotProject()
    val versionDir = "com/example/lib/1.0.0-SNAPSHOT"

    test("1st run - the repo is populated, and the tests run") {
      project.runner.withArguments(":tests:test").build {
        shouldHaveTaskWithOutcome(":tests:test", SUCCESS)

        project.repoFile("$versionDir/maven-metadata.xml").shouldExist()
      }
    }

    test("expect a re-publish changes only maven-metadata and the SNAPSHOT file names") {
      val beforeNames = project.repoFileNames(versionDir)
      val before = project.repoContents()

      project.file("lib/build").deleteRecursively()

      project.runner
        .withArguments(
          ":tests:test",
          // Shift the clock, otherwise if re-publish is fast then it could reuse the previous SNAPSHOT timestamp.
          // This test requires the SNAPSHOT timestamp is different, to prove Dev Publish ignores filenames.
          "-Dorg.gradle.internal.test.clockoffset=60000",
        )
        .build {
          shouldHaveTaskWithOutcome(":tests:test", UP_TO_DATE)
          shouldHaveTaskWithOutcome(":lib:publishMavenJavaPublicationToDevPublishMavenRepository", SUCCESS)

          val afterNames = project.repoFileNames(versionDir)

          // the re-publish must rename the files, otherwise the assertions below can't tell a
          // name-insensitive checksum from one that includes file names
          afterNames shouldNotBe beforeNames

          // old SNAPSHOT files are replaced, not accumulated
          afterNames.size shouldBe beforeNames.size

          // identical contents under new names, so the `.module` does not refer to the unique version
          project.repoContents() shouldBe before
        }
    }

    test("expect the out-of-context artifact is in the repo") {
      project.runner.withArguments(":tests:test").build {
        shouldHaveTaskWithOutcome(":lib:publishMavenJavaPublicationToDevPublishMavenRepository", UP_TO_DATE)

        val extra = project.repoFileNames(versionDir).single { it.endsWith("-extra.txt") }
        project.repoFile("$versionDir/$extra").readText().trim() shouldBe "v1"
      }
    }

    test("expect a re-publish after deleting both build dirs restores the consumer's cached result") {
      project.file("lib/build").deleteRecursively()
      project.file("tests/build").deleteRecursively()

      project.runner.withArguments(":tests:test").build {
        shouldHaveTaskWithOutcome(":lib:publishMavenJavaPublicationToDevPublishMavenRepository", SUCCESS)
        shouldHaveTaskWithOutcome(":tests:test", FROM_CACHE)
      }
    }
  }
}) {

  override fun testCaseOrder(): TestCaseOrder = TestCaseOrder.Sequential

  companion object {

    private fun GradleProjectTest.repoDir(): Path =
      file("tests/build/maven-dev/")

    /** A file in the consumer's dev Maven repository. */
    private fun GradleProjectTest.repoFile(path: String): Path =
      repoDir().resolve(path)

    private fun GradleProjectTest.repoFileNames(dir: String): List<String> =
      repoFile(dir).listDirectoryEntries().map { it.name }.sorted()

    /**
     * `{directory}:{digest}` of every repo file except `maven-metadata.xml*`.
     *
     * File names are left out, because a SNAPSHOT re-publish renames every file.
     */
    private fun GradleProjectTest.repoContents(): List<String> {
      return repoDir().walk()
        .filter { it.isRegularFile() && !it.name.startsWith("maven-metadata.xml") }
        .map { file ->
          val parentDir = file.parent.relativeTo(repoDir()).invariantSeparatorsPathString
          val digest = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes())
            .toHexString()
          "$parentDir:$digest"
        }
        .sorted()
        .toList()
    }

    private fun GradleProjectTest.repoJarEntries(path: String): List<String> =
      ZipFile(repoFile(path).toFile()).use { zip ->
        zip.entries().asSequence().map { it.name }.toList()
      }

    private fun GradleProjectTest.repoJarEntryText(path: String, entry: String): String =
      ZipFile(repoFile(path).toFile()).use { zip ->
        val zipEntry = requireNotNull(zip.getEntry(entry)) { "no $entry in ${repoFile(path)}" }
        zip.getInputStream(zipEntry).reader().readText()
      }

    /**
     * Publication name, artifact id and POM are all non-default, so nothing can be hardcoded.
     *
     * Built with [buildString], because a nested `trimMargin` would leave a stray `|` prefix.
     */
    private fun libBuildGradleKts(
      pomDescription: String? = null,
      dependOnDep: Boolean = false,
    ): String = buildString {
      appendLine("plugins {")
      appendLine("  kotlin(\"jvm\")")
      appendLine("  `maven-publish`")
      appendLine("  id(\"dev.adamko.dev-publish\") version \"+\"")
      appendLine("}")
      appendLine()
      appendLine("group = \"com.example\"")
      appendLine("version = \"1.0.0\"")
      appendLine()
      // both jars become variants in Gradle Module Metadata
      appendLine("java {")
      appendLine("  withSourcesJar()")
      appendLine("  withJavadocJar()")
      appendLine("}")
      appendLine()
      if (dependOnDep) {
        appendLine("dependencies {")
        appendLine("  implementation(project(\":dep\"))")
        appendLine("}")
        appendLine()
      }
      // published through the software component, so it IS described by Gradle Module Metadata
      appendLine("val trackedJar by tasks.registering(Jar::class) {")
      appendLine("  archiveClassifier = \"tracked\"")
      appendLine("  from(file(\"src/tracked\"))")
      appendLine("}")
      appendLine()
      appendLine("val trackedElements = configurations.consumable(\"trackedElements\") {")
      appendLine("  attributes {")
      appendLine("    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))")
      appendLine("    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))")
      appendLine("    attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))")
      appendLine("    attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(\"tracked\"))")
      appendLine("  }")
      appendLine("  outgoing.artifact(trackedJar)")
      appendLine("}")
      appendLine()
      appendLine("(components[\"java\"] as AdhocComponentWithVariants)")
      // `.get()` - Gradle 8 has no Provider overload
      appendLine("  .addVariantsFromConfiguration(trackedElements.get()) {")
      appendLine("    mapToMavenScope(\"runtime\")")
      appendLine("    mapToOptional()")
      appendLine("  }")
      appendLine()
      appendLine("publishing {")
      appendLine("  publications {")
      appendLine("    create<MavenPublication>(\"customPub\") {")
      appendLine("      artifactId = \"renamed-lib\"")
      appendLine("      from(components[\"java\"])")
      // not part of the software component, so not in Gradle Module Metadata
      appendLine("      artifact(file(\"src/extra/payload.txt\")) {")
      appendLine("        classifier = \"extra\"")
      appendLine("        extension = \"txt\"")
      appendLine("      }")
      if (pomDescription != null) {
        appendLine("      pom { description = \"$pomDescription\" }")
      }
      appendLine("    }")
      appendLine("  }")
      appendLine("}")
    }

    /**
     * A default publication, plus a POM-only `relocation` publication.
     *
     * Deliberately minimal - the relocation context asserts only on the relocation POM, so it has no
     * use for the sources, javadoc, tracked or directly-declared artifacts in [libBuildGradleKts].
     */
    private fun relocationBuildGradleKts(relocateTo: String): String = buildString {
      appendLine("plugins {")
      appendLine("  kotlin(\"jvm\")")
      appendLine("  `maven-publish`")
      appendLine("  id(\"dev.adamko.dev-publish\") version \"+\"")
      appendLine("}")
      appendLine()
      appendLine("group = \"com.example\"")
      appendLine("version = \"1.0.0\"")
      appendLine()
      appendLine("publishing {")
      appendLine("  publications {")
      appendLine("    create<MavenPublication>(\"mavenJava\") {")
      appendLine("      from(components[\"java\"])")
      appendLine("    }")
      // https://docs.gradle.org/current/userguide/publishing_maven.html#publishing_maven:retroactive_relocation
      appendLine("    create<MavenPublication>(\"relocation\") {")
      appendLine("      artifactId = \"old-name\"")
      appendLine("      pom {")
      appendLine("        distributionManagement {")
      appendLine("          relocation {")
      appendLine("            artifactId = \"$relocateTo\"")
      appendLine("            message = \"renamed\"")
      appendLine("          }")
      appendLine("        }")
      appendLine("      }")
      appendLine("    }")
      appendLine("  }")
      appendLine("}")
    }

    private fun simpleLibBuildGradleKts(): String = buildString {
      appendLine("plugins {")
      appendLine("  kotlin(\"jvm\")")
      appendLine("  `maven-publish`")
      appendLine("  id(\"dev.adamko.dev-publish\") version \"+\"")
      appendLine("}")
      appendLine()
      appendLine("group = \"com.example\"")
      appendLine("version = \"1.0.0\"")
      appendLine()
      // Gradle 8 archives keep file timestamps, so a rebuilt jar never matches a cached result
      appendLine("tasks.withType<AbstractArchiveTask>().configureEach {")
      appendLine("  isPreserveFileTimestamps = false")
      appendLine("  isReproducibleFileOrder = true")
      appendLine("}")
      appendLine()
      appendLine("publishing {")
      appendLine("  publications {")
      appendLine("    create<MavenPublication>(\"mavenJava\") {")
      appendLine("      from(components[\"java\"])")
      appendLine("    }")
      appendLine("  }")
      appendLine("}")
    }

    /**
     * Writes a stub Maven repository holding pom-only `com.example:upstream` modules.
     *
     * `maven-metadata.xml` is required, otherwise a dynamic version has no version list to match.
     */
    private fun ProjectDirectoryScope.publishUpstream(vararg versions: String) {
      versions.forEach { version ->
        createFile(
          "stub-repo/com/example/upstream/$version/upstream-$version.pom",
          buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("<project>")
            appendLine("  <modelVersion>4.0.0</modelVersion>")
            appendLine("  <groupId>com.example</groupId>")
            appendLine("  <artifactId>upstream</artifactId>")
            appendLine("  <version>$version</version>")
            appendLine("  <packaging>pom</packaging>")
            appendLine("</project>")
          }
        )
      }

      createFile(
        "stub-repo/com/example/upstream/maven-metadata.xml",
        buildString {
          appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
          appendLine("<metadata>")
          appendLine("  <groupId>com.example</groupId>")
          appendLine("  <artifactId>upstream</artifactId>")
          appendLine("  <versioning>")
          appendLine("    <latest>${versions.last()}</latest>")
          appendLine("    <versions>")
          versions.forEach { appendLine("      <version>$it</version>") }
          appendLine("    </versions>")
          appendLine("  </versioning>")
          appendLine("</metadata>")
        }
      )
    }

    private fun versionMappingBuildGradleKts(): String = buildString {
      appendLine("plugins {")
      appendLine("  kotlin(\"jvm\")")
      appendLine("  `maven-publish`")
      appendLine("  id(\"dev.adamko.dev-publish\") version \"+\"")
      appendLine("}")
      appendLine()
      appendLine("group = \"com.example\"")
      appendLine("version = \"1.0.0\"")
      appendLine()
      appendLine("repositories {")
      appendLine("  maven { url = uri(\"../stub-repo\") }")
      appendLine("  mavenCentral()")
      appendLine("}")
      appendLine()
      // otherwise Gradle caches the dynamic version for 24h and the new one is never seen
      appendLine("configurations.all {")
      appendLine("  resolutionStrategy.cacheDynamicVersionsFor(0, \"seconds\")")
      appendLine("}")
      appendLine()
      appendLine("dependencies {")
      appendLine("  implementation(\"com.example:upstream:1.+\")")
      appendLine("}")
      appendLine()
      appendLine("publishing {")
      appendLine("  publications {")
      appendLine("    create<MavenPublication>(\"mavenJava\") {")
      appendLine("      from(components[\"java\"])")
      appendLine("      versionMapping {")
      appendLine("        usage(\"java-api\") { fromResolutionResult() }")
      appendLine("        usage(\"java-runtime\") { fromResolutionResult() }")
      appendLine("      }")
      appendLine("    }")
      appendLine("  }")
      appendLine("}")
    }

    /**
     * A SNAPSHOT with an out-of-context artifact.
     *
     * Archives are reproducible, so on Gradle 8 a rebuilt jar is byte-identical, and only
     * re-publishing can introduce a difference.
     */
    private fun snapshotLibBuildGradleKts(): String = buildString {
      appendLine("plugins {")
      appendLine("  kotlin(\"jvm\")")
      appendLine("  `maven-publish`")
      appendLine("  id(\"dev.adamko.dev-publish\") version \"+\"")
      appendLine("}")
      appendLine()
      appendLine("group = \"com.example\"")
      appendLine("version = \"1.0.0-SNAPSHOT\"")
      appendLine()
      appendLine("tasks.withType<AbstractArchiveTask>().configureEach {")
      appendLine("  isPreserveFileTimestamps = false")
      appendLine("  isReproducibleFileOrder = true")
      appendLine("}")
      appendLine()
      appendLine("publishing {")
      appendLine("  publications {")
      appendLine("    create<MavenPublication>(\"mavenJava\") {")
      appendLine("      from(components[\"java\"])")
      appendLine("      artifact(file(\"src/extra/payload.txt\")) {")
      appendLine("        classifier = \"extra\"")
      appendLine("        extension = \"txt\"")
      appendLine("      }")
      appendLine("    }")
      appendLine("  }")
      appendLine("}")
    }

    private fun pluginBuildGradleKts(displayName: String): String = buildString {
      appendLine("plugins {")
      appendLine("  kotlin(\"jvm\")")
      appendLine("  `java-gradle-plugin`")
      appendLine("  `maven-publish`")
      appendLine("  id(\"dev.adamko.dev-publish\") version \"+\"")
      appendLine("}")
      appendLine()
      appendLine("group = \"com.example\"")
      appendLine("version = \"1.0.0\"")
      appendLine()
      appendLine("gradlePlugin {")
      appendLine("  plugins.register(\"greeting\") {")
      appendLine("    id = \"com.example.greeting\"")
      appendLine("    implementationClass = \"com.example.GreetingPlugin\"")
      appendLine("    displayName = \"$displayName\"")
      appendLine("  }")
      appendLine("}")
    }

    private fun consumerBuildGradleKts(producer: String): String = buildString {
      appendLine("plugins {")
      appendLine("  kotlin(\"jvm\")")
      appendLine("  id(\"dev.adamko.dev-publish\") version \"+\"")
      appendLine("}")
      appendLine()
      appendLine("dependencies {")
      appendLine("  devPublication(project(\"$producer\"))")
      appendLine()
      appendLine("  testImplementation(kotlin(\"test\"))")
      appendLine("  testImplementation(devPublish.dependency())")
      appendLine("}")
      appendLine()
      appendLine("tasks.test {")
      appendLine("  useJUnitPlatform()")
      appendLine("}")
    }

    /**
     * Wipes `src` and `build` of every module, so a fixture never inherits a source file that an
     * earlier version of this spec wrote, or that one of its own mutations added.
     */
    private fun ProjectDirectoryScope.clean(vararg modules: String) {
      file("local-cache").deleteRecursively()
      modules.forEach { module ->
        file("$module/src").deleteRecursively()
        file("$module/build").deleteRecursively()
      }
    }

    /** Built with [buildString], because a nested `trimMargin` would leave stray `|` prefixes. */
    private fun cacheSettings(vararg includes: String): String = buildString {
      appendLine("include(")
      includes.forEach { appendLine("""  "$it",""") }
      appendLine(")")
      appendLine()
      appendLine("buildCache {")
      appendLine("  local {")
      appendLine("""    directory = file("local-cache")""")
      appendLine("  }")
      appendLine("}")
    }

    /**
     * The consumer's only test reads the dev repo, so its test task depends on dev publishing.
     *
     * It always passes - this spec asserts on task outcomes and on the repo's contents, not on
     * anything the build under test reports.
     */
    private fun ProjectDirectoryScope.consumerTestSource() {
      createKotlinFile(
        "src/test/kotlin/DevRepoTest.kt",
        """
          |import dev.adamko.gradle.dev_publish.devMavenRepo
          |import kotlin.io.path.isDirectory
          |import kotlin.test.Test
          |import kotlin.test.assertTrue
          |
          |class DevRepoTest {
          |
          |  @Test
          |  fun `dev repo is readable`() {
          |    val repo = devMavenRepo()
          |    assertTrue(repo.isDirectory(), "expected a dev Maven repository at ${'$'}repo")
          |  }
          |}
          |""".trimMargin()
      )
    }

    /**
     * Gradle 8 embeds Kotlin 2.0, which cannot read `dev-publish-utils` (minimum Kotlin 2.2.21).
     * Gradle 9 embeds a newer Kotlin, which a plugin project must use to compile against `gradleApi()`.
     */
    private fun ProjectDirectoryScope.rootBuildGradleKts() {
      val kotlinVersion = if (testedGradleVersion >= "9.0") "embeddedKotlinVersion" else "\"2.2.21\""
      buildGradleKts = """
        |plugins {
        |  kotlin("jvm") version $kotlinVersion apply false
        |}
        |""".trimMargin()
    }

    private fun TestScope.libraryProject(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "dev-repo-staleness-library",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {
        clean("lib", "tests")

        settingsGradleKts += cacheSettings(":lib", ":tests")
        rootBuildGradleKts()

        dir("lib") {
          buildGradleKts = simpleLibBuildGradleKts()
          createKotlinFile(
            "src/main/kotlin/com/example/lib/LibClass.kt",
            """
              |package com.example.lib
              |
              |class LibClass
              |""".trimMargin()
          )
        }

        dir("tests") {
          buildGradleKts = consumerBuildGradleKts(":lib")
          consumerTestSource()
        }
      }

    private fun TestScope.customPublicationProject(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "dev-repo-staleness-custom-publication",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {
        clean("lib", "dep", "tests")

        settingsGradleKts += cacheSettings(":lib", ":dep", ":tests")
        rootBuildGradleKts()

        dir("dep") {
          buildGradleKts = simpleLibBuildGradleKts()
          createKotlinFile(
            "src/main/kotlin/com/example/dep/DepClass.kt",
            """
              |package com.example.dep
              |
              |class DepClass
              |""".trimMargin()
          )
        }

        dir("lib") {
          buildGradleKts = libBuildGradleKts()
          createKotlinFile(
            "src/main/kotlin/com/example/lib/LibClass.kt",
            """
              |package com.example.lib
              |
              |class LibClass
              |""".trimMargin()
          )
          createFile("src/main/resources/lib.properties", "name=lib\n")
          // separate directories, so the tracked and untracked artifact tests cannot confound
          createFile("src/extra/payload.txt", "v1\n")
          createFile("src/tracked/payload.txt", "v1\n")
        }

        dir("tests") {
          buildGradleKts = consumerBuildGradleKts(":lib")
          consumerTestSource()
        }
      }

    private fun TestScope.relocationProject(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "dev-repo-staleness-relocation",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {
        clean("lib", "tests")

        settingsGradleKts += cacheSettings(":lib", ":tests")
        rootBuildGradleKts()

        dir("lib") {
          buildGradleKts = relocationBuildGradleKts(relocateTo = "renamed-lib")
          createKotlinFile(
            "src/main/kotlin/com/example/lib/LibClass.kt",
            """
              |package com.example.lib
              |
              |class LibClass
              |""".trimMargin()
          )
        }

        dir("tests") {
          buildGradleKts = consumerBuildGradleKts(":lib")
          consumerTestSource()
        }
      }

    private fun TestScope.versionMappingProject(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "dev-repo-staleness-version-mapping",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {
        clean("lib", "tests")
        file("stub-repo").deleteRecursively()

        settingsGradleKts += cacheSettings(":lib", ":tests")
        rootBuildGradleKts()

        publishUpstream("1.0")

        dir("lib") {
          buildGradleKts = versionMappingBuildGradleKts()
          createKotlinFile(
            "src/main/kotlin/com/example/lib/LibClass.kt",
            """
              |package com.example.lib
              |
              |class LibClass
              |""".trimMargin()
          )
        }

        dir("tests") {
          buildGradleKts = consumerBuildGradleKts(":lib")
          consumerTestSource()
        }
      }

    private fun TestScope.snapshotProject(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "dev-repo-staleness-snapshot",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {
        clean("lib", "tests")

        settingsGradleKts += cacheSettings(":lib", ":tests")
        rootBuildGradleKts()

        dir("lib") {
          buildGradleKts = snapshotLibBuildGradleKts()
          createKotlinFile(
            "src/main/kotlin/com/example/lib/LibClass.kt",
            """
              |package com.example.lib
              |
              |class LibClass
              |""".trimMargin()
          )
          createFile("src/extra/payload.txt", "v1\n")
        }

        dir("tests") {
          buildGradleKts = consumerBuildGradleKts(":lib")
          consumerTestSource()
        }
      }

    private fun TestScope.gradlePluginProject(): GradleProjectTest =
      gradleKtsProjectTest(
        projectName = "dev-repo-staleness-plugin-marker",
        testProjectPath = testCase.descriptor.slashSeparatedPath(),
      ) {
        clean("plugin", "tests")

        settingsGradleKts += cacheSettings(":plugin", ":tests")
        rootBuildGradleKts()

        dir("plugin") {
          buildGradleKts = pluginBuildGradleKts(displayName = "Greeting plugin")
          createKotlinFile(
            "src/main/kotlin/com/example/GreetingPlugin.kt",
            """
              |package com.example
              |
              |import org.gradle.api.Plugin
              |import org.gradle.api.Project
              |
              |class GreetingPlugin : Plugin<Project> {
              |  override fun apply(target: Project) {}
              |}
              |""".trimMargin()
          )
        }

        dir("tests") {
          buildGradleKts = consumerBuildGradleKts(":plugin")
          consumerTestSource()
        }
      }
  }
}
