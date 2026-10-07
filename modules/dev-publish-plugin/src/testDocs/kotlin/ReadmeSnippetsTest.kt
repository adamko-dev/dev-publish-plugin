package dev.adamko.gradle.dev_publish

import dev.adamko.gradle.dev_publish.test_utils.repoRootDir
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.file.shouldBeAFile
import io.kotest.matchers.shouldBe

/**
 * Verifies that every code snippet in the README appears verbatim in one of the runnable projects
 * in `examples/`, so the docs cannot drift away from code that is actually built and tested.
 *
 * See `CONTRIBUTING.md` for how to mark a snippet.
 */
class ReadmeSnippetsTest : FunSpec({

  val rootDir = repoRootDir.toFile()
  val readme = rootDir.resolve("README.md")

  val snippetChecks = snippetMarker.findAll(readme.readText())
    .mapNotNull { match ->
      val path = snippetLink.find(match.groupValues[2])?.groupValues?.get(1)
      if (path == null) null else match to path
    }
    .map { (match, path) ->
      val segments = splitOnElisions(match.groupValues[1].toComparableLines())
      val sourceLines = rootDir.resolve(path).takeIf { it.isFile }?.readText()?.toComparableLines()
      SnippetCheck(
        path = path,
        expected = joinSegments(segments),
        actual = sourceLines?.let { source -> joinSegments(segments.map { source.closestRegion(it) }) },
      )
    }
    .toList()

  test("expect the README contains snippets") {
    snippetChecks.shouldNotBeEmpty()
  }

  snippetChecks.forEachIndexed { index, check ->
    test("expect README snippet ${index + 1} appears in ${check.path}") {
      rootDir.resolve(check.path).shouldBeAFile()

      withClue(
        "this README snippet is not a contiguous region of ${check.path} any more - " +
            "update the README to match the example, or the example to match the README"
      ) {
        check.actual shouldBe check.expected
      }
    }
  }
})

/** A README snippet, and the region of the example it should match. */
private class SnippetCheck(val path: String, val expected: String, val actual: String?)

/**
 * The lines most like [segment]. An exact match wins, so a passing snippet gets its own lines back.
 */
private fun List<String>.closestRegion(segment: List<String>): List<String> =
  windowed(segment.size)
    .maxByOrNull { window -> window.zip(segment).count { (a, b) -> a == b } }
    .orEmpty()

private fun joinSegments(segments: List<List<String>>): String =
  segments.joinToString("\n// ...\n") { it.joinToString("\n") }

/** Splits on `// ...` lines, which mark lines the README left out. */
private fun splitOnElisions(chunk: List<String>): List<List<String>> =
  chunk
    .fold(ArrayDeque<ArrayDeque<String>>()) { acc, line ->
      if (line.trim() == "// ...") {
        acc.addLast(ArrayDeque())
      } else {
        if (acc.isEmpty()) acc.addLast(ArrayDeque())
        acc.last().addLast(line)
      }
      acc
    }
    .map { it.dropWhile(String::isBlank).dropLastWhile(String::isBlank) }
    .filter { it.isNotEmpty() }


/** A code block, followed by the quote that links to the file it came from. */
// The lookahead stops a snippet spanning a code block that has no quote after it.
private val snippetMarker =
  Regex("""```[a-z]*\n((?:(?!```)[\s\S])*)```\n\n((?:>[^\n]*\n)+)""")

/** The link to the example file, wherever in the quote an editor wrapped it. */
private val snippetLink = Regex("""\[here]\((\S+)\)""")

private fun String.toComparableLines(): List<String> =
  lines()
    .map {
      it.trim()
        // The plugin version differs between the docs and the examples, so ignore it.
        .replace(
          Regex("""(id\s*\(?['"]dev\.adamko\.dev-publish['"]\)?\s+version\s+)['"][^'"]+['"]"""),
          "$1<version>",
        )
    }
    .dropWhile { it.isBlank() }
    .dropLastWhile { it.isBlank() }
