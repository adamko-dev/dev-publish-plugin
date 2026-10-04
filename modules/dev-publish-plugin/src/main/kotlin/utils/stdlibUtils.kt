package dev.adamko.gradle.dev_publish.utils

import java.io.IOException
import java.io.OutputStream
import kotlin.concurrent.Volatile

/** Split a string to a [Pair], using [substringBefore] and [substringAfter] */
internal fun String.splitToPair(delimiter: String): Pair<String, String> =
  substringBefore(delimiter) to substringAfter(delimiter, "")

internal fun String.uppercaseFirstChar(): String =
  replaceFirstChar { it.uppercase() }

/**
 * Replace with [OutputStream.nullOutputStream]
 * when the minimum supported Java version is 11+.
 */
internal fun nullOutputStream() =
  object : OutputStream() {
    @Volatile
    private var closed = false

    @Throws(IOException::class)
    private fun ensureOpen() {
      if (closed) {
        throw IOException("Stream closed")
      }
    }

    @Throws(IOException::class)
    override fun write(b: Int) {
      ensureOpen()
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
      checkFromIndexSize(off, len, b.size)
      ensureOpen()
    }

    override fun close() {
      closed = true
    }
  }

private fun checkFromIndexSize(
  fromIndex: Int, size: Int, length: Int
) {
  if ((length or fromIndex or size) < 0 || size > length - fromIndex) {
    throw IndexOutOfBoundsException("Range [$fromIndex, $fromIndex + $size) out of bounds for length $length")
  }
}
