package dev.adamko.gradle.dev_publish.test_utils


/** Replace characters that don't match [isLetterOrDigit] with [replacement]. */
fun String.replaceNonAlphaNumeric(replacement: String = "-"): String =
  map { if (it.isLetterOrDigit()) it else replacement }.joinToString("")
