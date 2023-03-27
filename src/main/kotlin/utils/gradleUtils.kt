package dev.adamko.gradle.dev_publish.utils

import org.gradle.api.artifacts.Configuration


/**
 * Mark this [Configuration] as one that will be consumed by other subprojects.
 *
 * ```
 * isCanBeResolved = false
 * isCanBeConsumed = true
 * ```
 */
internal fun Configuration.asProvider(
  visible: Boolean = true
) {
  isCanBeResolved = false
  isCanBeConsumed = true
  isVisible = visible
}


/**
 * Mark this [Configuration] as one that will consume artifacts from other subprojects (also known as 'resolving')
 *
 * ```
 * isCanBeResolved = true
 * isCanBeConsumed = false
 * ```
 * */
internal fun Configuration.asConsumer(
  visible: Boolean = false
) {
  isCanBeResolved = true
  isCanBeConsumed = false
  isVisible = visible
}
