package dev.adamko.gradle.dev_publish.test_utils

import org.gradle.util.GradleVersion

/** Compare a [GradleVersion] to a [version]. */
operator fun GradleVersion.compareTo(version: String): Int =
  compareTo(GradleVersion.version(version))
