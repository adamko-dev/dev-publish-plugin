package dev.adamko.gradle.dev_publish.utils

import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RelativePath


/**
 * Mark this [Configuration] as one that will be consumed by other subprojects.
 *
 * ```
 * isCanBeResolved = false
 * isCanBeConsumed = true
 * isCanBeDeclared = false
 * ```
 */
internal fun Configuration.asProvider(
  visible: Boolean = true
) {
  isCanBeResolved = false
  isCanBeConsumed = true
  isCanBeDeclared = false
  isVisible = visible
}


/**
 * Mark this [Configuration] as one that will fetch artifacts (also known as 'resolving').
 *
 * ```
 * isCanBeResolved = true
 * isCanBeConsumed = false
 * isCanBeDeclared = false
 * ```
 * */
internal fun Configuration.asConsumer(
  visible: Boolean = false
) {
  isCanBeResolved = true
  isCanBeConsumed = false
  isCanBeDeclared = false
  isVisible = visible
}


/**
 * Mark this [Configuration] for declaring dependencies in the `dependencies {}` block.
 *
 * ```
 * isCanBeResolved = false
 * isCanBeConsumed = false
 * isCanBeDeclared = true
 * ```
 * */
internal fun Configuration.forDependencies(
  visible: Boolean = false
) {
  isCanBeResolved = false
  isCanBeConsumed = false
  isCanBeDeclared = true
  isVisible = visible
}


/** Drop the first [count] directories from the [RelativePath] */
internal fun RelativePath.dropDirectories(count: Int): RelativePath =
  RelativePath(true, *segments.drop(count).toTypedArray())


/** Drop the first directory from the [RelativePath] */
internal fun RelativePath.dropDirectory(): RelativePath =
  dropDirectories(1)
