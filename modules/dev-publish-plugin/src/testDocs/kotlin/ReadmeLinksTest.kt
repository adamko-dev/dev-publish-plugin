package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.repoRootDir
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import java.io.File
import org.gradle.util.GradleVersion

/** Checks that the local links in every Markdown file still point at something. */
class ReadmeLinksTest : FunSpec({

  // TODO update to use NIO Path
  val rootDir = repoRootDir.toFile()

  val markdownFiles = rootDir.walk()
    .onEnter { it.name != "build" && it.name != ".gradle" && it.name != ".git" }
    .filter { it.isFile && it.extension == "md" }
    .toList()

  fun File.relativePath(): String = relativeTo(rootDir).invariantSeparatorsPath

  /** Drops inline code spans, one line at a time, so an unbalanced backtick stays contained. */
  fun String.withoutInlineCode(): String =
    lines().joinToString("\n") { line ->
      line.split("`").filterIndexed { index, _ -> index % 2 == 0 }.joinToString(" ")
    }

  /** Markdown links, excluding anything that is code rather than a link. */
  fun File.links(): List<Pair<String, String>> =
    readText()
      .replace(Regex("```.*?```", RegexOption.DOT_MATCHES_ALL), "")
      .withoutInlineCode()
      .let { Regex("""\[([^\[\]]*)]\(([^)\s]+)\)""").findAll(it) }
      .map { it.groupValues[1] to it.groupValues[2] }
      .toList()

  test("expect the docs contain links") {
    markdownFiles.flatMap { it.links() }.shouldNotBeEmpty()
  }

  context("local links") {
    test("expect every local link points at a file that exists") {
      // links are relative to the file they are in, not to the repo root
      val missing = markdownFiles.flatMap { file ->
        file.links()
          .map { (_, target) -> target }
          .filterNot { it.startsWith("http") || it.startsWith("#") }
          .distinct()
          .filterNot { file.parentFile.resolve(it.substringBefore('#')).exists() }
          .map { "${file.relativePath()} -> $it" }
      }

      withClue("these links do not exist on disk: $missing") {
        missing.shouldBeEmpty()
      }
    }
  }

  context("in-page anchors") {
    /** Mimics how GitHub derives an anchor from a heading. */
    fun String.toAnchor(): String =
      lowercase()
        .replace(Regex("""[^a-z0-9 _-]"""), "")
        .trim()
        .replace(' ', '-')

    test("expect every in-page link points at a heading") {
      val missing = markdownFiles.flatMap { file ->
        val headingAnchors = file.readLines()
          .filter { it.matches(Regex("""#{1,6} .*""")) }
          .map { it.substringAfter(' ').trim().toAnchor() }
          .toSet()

        file.links()
          .map { (_, target) -> target }
          .filter { it.startsWith("#") }
          .distinct()
          .filterNot { it.removePrefix("#") in headingAnchors }
          .map { "${file.relativePath()} -> $it" }
      }

      withClue("these anchors do not match a heading: $missing") {
        missing.shouldBeEmpty()
      }
    }
  }

  context("Gradle documentation links are pinned") {
    val gradleDocLinks = markdownFiles.flatMap { file ->
      Regex("""https://docs\.gradle\.org/([^/]+)/""")
        .findAll(file.readText())
        .map { file.relativePath() to it.groupValues[1] }
    }

    test("expect no links to the 'current' Gradle docs, which change with every release") {
      val unpinned = gradleDocLinks.filter { (_, version) -> version == "current" }
      withClue("these files link to docs.gradle.org/current/: $unpinned") {
        unpinned.shouldBeEmpty()
      }
    }

    test("expect Gradle docs links use the same version as Gradle") {
      val gradleVersion = GradleVersion.current().version

      val wrongVersion = gradleDocLinks
        .filter { (_, version) -> version != gradleVersion }
        .distinct()

      withClue(
        "this build runs Gradle $gradleVersion, but these links point at other versions: " +
            "$wrongVersion. Update the links, or pin them deliberately."
      ) {
        wrongVersion.shouldBeEmpty()
      }
    }
  }
})