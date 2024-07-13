package dev.adamko.gradle.dev_publish.utils

/**
 * Small util to build a pretty tabulated string.
 *
 * Columns will be padded to be of equal size, and left-aligned.
 */
internal class StringTableBuilder private constructor() {

  private data class Row(val values: List<String>) : List<String> by values

  private val rows: MutableList<Row> = mutableListOf()

  fun row(vararg values: String) {
    val row = Row(values.asList())
    rows.add(row)
  }

  private fun render(): String {
    val maxColWidths = maxColWidths()

    return buildString {
      rows.forEach { row ->
        row.forEachIndexed { colIndex, value ->
          val colWidth = maxColWidths[colIndex]
          append(value.padEnd(colWidth) + " ")
        }
        appendLine()
      }
    }
  }

  private fun maxColWidths(): List<Int> {
    val columns = rows.maxOfOrNull { row -> row.size } ?: 0

    fun maxWidthForColumn(colIndex: Int): Int =
      rows.maxOfOrNull { row ->
        (row.getOrNull(colIndex) ?: "").length
      } ?: 0

    return List(columns) { colIndex ->
      maxWidthForColumn(colIndex)
    }
  }

  internal companion object {
    fun buildTable(build: StringTableBuilder.() -> Unit): String =
      StringTableBuilder().apply(build).render()
  }
}
