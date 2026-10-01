package dev.adamko.gradle.dev_publish.test_utils

import io.kotest.core.descriptors.Descriptor
import io.kotest.core.descriptors.Descriptor.SpecDescriptor
import io.kotest.core.descriptors.Descriptor.TestDescriptor

// util for creating a distinct subdirectory for each test case
tailrec fun Descriptor.slashSeparatedPath(suffix: String = ""): String {
  return when (this) {
    is SpecDescriptor -> id.value.removePrefix("dev.adamko.gradle.dev_publish.") + suffix
    is TestDescriptor -> parent.slashSeparatedPath("/${id.value.replaceNonAlphaNumeric()}")
  }
}
