package dev.adamko.gradle.dev_publish.internal.checksums

import dev.adamko.gradle.dev_publish.internal.checksums.CreatePublicationChecksum.Companion.FileChecksumSeparator
import dev.adamko.gradle.dev_publish.utils.StringTableBuilder.Companion.buildTable
import dev.adamko.gradle.dev_publish.utils.splitToPair
import kotlin.math.max
import org.gradle.api.provider.Provider


/** Debug string of the [currentChecksum] and [storedChecksum] side-by-side */
internal fun checksumsToDebugString(
  currentChecksum: Provider<String>,
  storedChecksum: Provider<String>,
): String {
  fun Provider<String>.fileChecksumsMap(): Map<String, List<String>> =
    getOrElse("")
      .lines()
      .filter { FileChecksumSeparator in it }
      .map { it.splitToPair(FileChecksumSeparator) }
      .groupBy({ it.first }, { it.second })

  val currentFileChecksums = currentChecksum.fileChecksumsMap()
  val storedFileChecksums = storedChecksum.fileChecksumsMap()

  val files = currentFileChecksums.keys + storedFileChecksums.keys

  val checksums = files.joinToString("\n") { file ->
    val currentChecksums = currentFileChecksums[file] ?: emptyList()
    val storedChecksums = storedFileChecksums[file] ?: emptyList()

    fun List<String>.getChecksum(i: Int): String =
      getOrElse(i) { "<missing>" }.trim()

    val joinedChecksums =
      buildTable {
        row("current", "stored", "match")
        repeat(max(currentChecksums.size, storedChecksums.size)) { i ->
          val current = currentChecksums.getChecksum(i)
          val stored = storedChecksums.getChecksum(i)
          val matches = if (current == stored) "✅" else "❌"
          row(current, stored, matches)
        }
      }.prependIndent("  ")

    "${file}\n${joinedChecksums}"
  }.ifBlank { "(no checksums)" }

  return """
        |--------
        |${checksums.prependIndent("  ")}
        |--------
        """.trimMargin()
}
