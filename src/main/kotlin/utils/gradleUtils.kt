package dev.adamko.gradle.dev_publish.utils

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.RelativePath
import org.gradle.kotlin.dsl.dependencies
import org.gradle.util.GradleVersion


/**
 * Mark this [Configuration] as one that should be used to declare dependencies in
 * [Project.dependencies] block.
 *
 * Declarable Configurations should be extended by [resolvable] and [consumable] Configurations.
 *
 * ```
 * isCanBeResolved = false
 * isCanBeConsumed = false
 * isCanBeDeclared = true
 * ```
 */
internal fun Configuration.declarable(
  visible: Boolean = false,
) {
  isCanBeResolved = false
  isCanBeConsumed = false
  canBeDeclared = true
  isVisible = visible
}


/**
 * Mark this [Configuration] as one that will be consumed by other subprojects.
 *
 * ```
 * isCanBeResolved = false
 * isCanBeConsumed = true
 * isCanBeDeclared = false
 * ```
 */
internal fun Configuration.consumable(
  visible: Boolean = false,
) {
  isCanBeResolved = false
  isCanBeConsumed = true
  canBeDeclared = false
  isVisible = visible
}


/**
 * Mark this [Configuration] as one that will consume (also known as 'resolving') artifacts from declared dependencies
 *
 * ```
 * isCanBeResolved = true
 * isCanBeConsumed = false
 * isCanBeDeclared = false
 * ```
 */
internal fun Configuration.resolvable(
  visible: Boolean = false,
) {
  isCanBeResolved = true
  isCanBeConsumed = false
  canBeDeclared = false
  isVisible = visible
}


/**
 * Enable/disable [Configuration.isCanBeDeclared] only if it is supported by the
 * [CurrentGradleVersion]
 *
 * This function should be removed when the minimal supported Gradle version is 8.2.
 */
private var Configuration.canBeDeclared: Boolean
  get() {
    return if (configurationIsCanBeDeclaredEnabled) {
      @Suppress("UnstableApiUsage")
      isCanBeDeclared
    } else {
      false
    }
  }
  set(value) {
    if (configurationIsCanBeDeclaredEnabled) {
      @Suppress("UnstableApiUsage")
      isCanBeDeclared = value
    } else {
      // do nothing
    }
  }

/** `true` if [Configuration.isCanBeDeclared] is valid for the current Gradle version. */
private val configurationIsCanBeDeclaredEnabled: Boolean = CurrentGradleVersion >= "8.2"


/** Shortcut for [GradleVersion.current] */
internal val CurrentGradleVersion: GradleVersion
  get() = GradleVersion.current()


/** Compare a [GradleVersion] to a [version]. */
internal operator fun GradleVersion.compareTo(version: String): Int =
  compareTo(GradleVersion.version(version))


/** Drop the first [count] directories from [RelativePath] */
internal fun RelativePath.dropDirectories(count: Int): RelativePath =
  RelativePath(true, *segments.drop(count).toTypedArray())


/** Drop the first directory from [RelativePath] */
internal fun RelativePath.dropDirectory(): RelativePath =
  dropDirectories(1)


/** Instantiate a new [Attribute] of type [T] */
internal inline fun <reified T> Attribute(name: String): Attribute<T> =
  Attribute.of(name, T::class.java)


internal operator fun <T> AttributeContainer.get(key: Attribute<T>): T? =
  getAttribute(key)
