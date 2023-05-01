package dev.adamko.gradle.dev_publish.utils

/** Split a string to a [Pair], using [substringBefore] and [substringAfter] */
internal fun String.splitToPair(delimiter: String): Pair<String, String> =
  substringBefore(delimiter) to substringAfter(delimiter, "")
