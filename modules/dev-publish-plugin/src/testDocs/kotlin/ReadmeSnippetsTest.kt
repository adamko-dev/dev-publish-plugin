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

  /** A code block, followed by the quote that links to the file it came from. */
  val snippetMarker =
    Regex(
      // The lookahead stops a snippet spanning a code block that has no quote after it.
      """```[a-z]*\n((?:(?!```)[\s\S])*)```\n\n((?:>[^\n]*\n)+)""",
    )

  /** The link to the example file, wherever in the quote an editor wrapped it. */
  val snippetLink = Regex("""\[here]\((\S+)\)""")

  /** The plugin version differs between the docs and the examples, so ignore it. */
  fun String.normalizeVersion(): String =
    replace(
      Regex("""(id\s*\(?['"]dev\.adamko\.dev-publish['"]\)?\s+version\s+)['"][^'"]+['"]"""),
      "$1<version>",
    )

  fun String.toComparableLines(): List<String> =
    normalizeVersion().lines().map { it.trimEnd() }.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }

  /** The indentation the region has in its source file. */
  fun List<String>.indentOf(): Int =
    filter { it.isNotBlank() }.minOfOrNull { line -> line.takeWhile { it == ' ' }.length } ?: 0

  /** Removes the indentation the region has in its source file, so nested code can be compared. */
  fun List<String>.dedent(indent: Int = indentOf()): List<String> =
    map { it.drop(indent) }

  /** Splits on `// ...` lines, which mark lines the README left out. */
  fun List<String>.splitOnElisions(): List<List<String>> =
    buildList {
      var segment = mutableListOf<String>()
      this@splitOnElisions.forEach { line ->
        if (line.trim() == "// ...") {
          add(segment)
          segment = mutableListOf()
        } else {
          segment += line
        }
      }
      add(segment)
    }
      .map { it.dropWhile(String::isBlank).dropLastWhile(String::isBlank) }
      .filter { it.isNotEmpty() }

  val snippets = snippetMarker.findAll(readme.readText())
    .mapNotNull { match ->
      val path = snippetLink.find(match.groupValues[2])?.groupValues?.get(1)
      if (path == null) null else path to match.groupValues[1]
    }
    .toList()

  test("expect the README contains snippets") {
    snippets.shouldNotBeEmpty()
  }

  snippets.forEachIndexed { index, (path, snippet) ->
    test("expect README snippet ${index + 1} appears in $path") {
      val sourceFile = rootDir.resolve(path)
      sourceFile.shouldBeAFile()

      val sourceLines = sourceFile.readText().toComparableLines()
      val segments = snippet.toComparableLines().splitOnElisions()

      // each segment must appear after the previous one, all at the same indentation
      var cursor = 0
      var indent: Int? = null

      segments.forEachIndexed { segmentIndex, segment ->
        val windows = (cursor..sourceLines.size - segment.size).map { start ->
          start to sourceLines.subList(start, start + segment.size)
        }

        val (start, window) = windows.firstOrNull { (_, window) ->
          window.dedent(indent ?: window.indentOf()) == segment
        } ?: run {
          // Compare the segment against the region of the file that is most similar to it, so that
          // the failure is a diff showing which lines drifted, and not just 'expected true'.
          val closest = windows
            .map { (_, window) -> window.dedent(indent ?: window.indentOf()) }
            .maxByOrNull { candidate -> candidate.zip(segment).count { (a, b) -> a == b } }
            .orEmpty()

          val part = if (segments.size > 1) " (part ${segmentIndex + 1} of ${segments.size})" else ""

          withClue(
            "this README snippet$part is not a contiguous region of $path any more - " +
                "update the README to match the example, or the example to match the README"
          ) {
            segment.joinToString("\n") shouldBe closest.joinToString("\n")
          }
          error("README snippet ${index + 1}$part does not appear in $path")
        }

        indent = indent ?: window.indentOf()
        cursor = start + segment.size
      }
    }
  }
})
