package dev.adamko.gradle.dev_publish.test_utils

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import kotlin.io.path.*

// based on https://gist.github.com/mfwgenerics/d1ec89eb80c95da9d542a03b49b5e15b
// context: https://kotlinlang.slack.com/archives/C0B8MA7FA/p1676106647658099

typealias PathGlobMatchersBuilder = PathGlobMatchers.() -> Unit

fun Path.toTreeString(
  filter: PathGlobMatchersBuilder = {}
): String {
  val matchers = PathGlobMatchersImpl().apply(filter)
  return when {
    isDirectory() -> name + "/\n" + buildTreeString(this, matchers = matchers)
    else          -> name
  }
}

private fun buildTreeString(
  dir: Path,
  margin: String = "",
  matchers: PathGlobMatchers,
): String {
  val entries = dir.listDirectoryEntries(matchers)
    .filter {
      it.isRegularFile() || !it.isEmptyDir(matchers)
    }

  return entries.joinToString("\n") { entry ->
    val (currentPrefix, nextPrefix) = when (entry) {
      entries.last() -> PrefixPair.LAST_ENTRY
      else           -> PrefixPair.INTERMEDIATE
    }

    buildString {
      append("$margin${currentPrefix}${entry.name}")

      if (entry.isDirectory()) {
        append("/")
        if (entry.countDirectoryEntries(matchers) > 0) {
          append("\n")
        }
        append(buildTreeString(entry, margin + nextPrefix, matchers))
      }
    }
  }
}

private fun Path.listDirectoryEntries(
  matchers: PathGlobMatchers,
): List<Path> =
  listDirectoryEntries()
    .filter { matchers.matches(it) }
    .sortedWith(FileSorter)


private fun Path.isEmptyDir(
  matchers: PathGlobMatchers
): Boolean =
  isRegularFile() ||
      walk().filter { matchers.matches(it) }.none { it.isRegularFile() }


private fun Path.countDirectoryEntries(
  filter: PathGlobMatchers
): Int =
  listDirectoryEntries(filter).count()


private data class PrefixPair(
  /** The current entry should be prefixed with this */
  val currentPrefix: String,
  /** If the next item is a directory, it should be prefixed with this */
  val nextPrefix: String,
) {
  companion object {
    /** Prefix pair for a non-last directory entry */
    val INTERMEDIATE = PrefixPair("├── ", "│   ")

    /** Prefix pair for the last directory entry */
    val LAST_ENTRY = PrefixPair("└── ", "    ")
  }
}


interface PathGlobMatchers {
  fun include(vararg globPatterns: String)
  fun excludes(vararg globPatterns: String)
  fun matches(path: Path): Boolean
  //fun matches(file: File): Boolean
}

private class PathGlobMatchersImpl : PathGlobMatchers {
  private val includes: MutableList<PathMatcher> = mutableListOf()

  private val excludes: MutableList<PathMatcher> = mutableListOf()

  override fun include(vararg globPatterns: String) {
    globPatterns.mapTo(includes) {
      FileSystems.getDefault().getPathMatcher("glob:$it")
    }
  }

  override fun excludes(vararg globPatterns: String) {
    globPatterns.mapTo(excludes) {
      FileSystems.getDefault().getPathMatcher("glob:$it")
    }
  }

  /**
   * * If there are no explicit [inclusions][includes] or [exclusions][excludes], everything is included.
   * * If at least one inclusion is specified, only files and directories matching the patterns are included.
   * * Any exclusion pattern overrides any inclusions, so if a file or directory matches at
   * least one exclusion pattern, it won’t be included, regardless of the inclusion patterns
   */
  override fun matches(path: Path): Boolean =
    if (path.isRegularFile()) {
      excludes.none { it.matches(path) } && includes.all { it.matches(path) }
    } else {
      true
    }

//  /** @see matches */
//  override fun matches(file: File): Boolean = matches(file.toPath())

}


/**
 * Directories before files, otherwise sort by filename.
 */
private object FileSorter : Comparator<Path> {
  override fun compare(o1: Path, o2: Path): Int {
    return when {
      o1.isDirectory() && o2.isRegularFile() -> -1 // directories before files
      o1.isRegularFile() && o2.isDirectory() -> +1 // files after directories
      else                                   -> o1.name.compareTo(o2.name)
    }
  }
}
