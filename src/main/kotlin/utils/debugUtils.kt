package dev.adamko.gradle.dev_publish.utils

import org.gradle.api.provider.Provider
import kotlin.math.max


/** Debug string of the [currentChecksum] and [storedChecksum] side-by-side */
internal fun checksumsToDebugString(
  currentChecksum: Provider<String>,
  storedChecksum: Provider<String>,
): String {
  fun Provider<String>.fileChecksumsMap() =
    getOrElse("")
      .lines()
      .filter { "=" in it }
      .map { it.splitToPair("=") }
      .groupBy({ it.first }, { it.second })

  val currentFileChecksums = currentChecksum.fileChecksumsMap()
  val storedFileChecksums = storedChecksum.fileChecksumsMap()

  val files = currentFileChecksums.keys + storedFileChecksums.keys

  val checksums = files.joinToString("\n") { file ->
    val currentChecksums = currentFileChecksums[file] ?: emptyList()
    val storedChecksums = storedFileChecksums[file] ?: emptyList()

    fun List<String>.getChecksum(i: Int) =
      getOrElse(i) { "<missing>" }.trim().padStart(40, ' ')

    val joinedChecksums = (0 until max(currentChecksums.size, storedChecksums.size))
      .joinToString("\n") { i ->
        val current = currentChecksums.getChecksum(i)
        val stored = storedChecksums.getChecksum(i)
        val matches = if (current == stored) "✅" else "❌"
        "  $current   $stored   $matches"
      }

    "$file\n$joinedChecksums"
  }

  return "current vs stored\n$checksums"
}
