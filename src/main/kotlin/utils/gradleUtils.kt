package dev.adamko.gradle.dev_publish.utils

import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.RelativePath
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.util.GradleVersion
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KFunction1
import kotlin.reflect.KProperty


internal val CurrentGradleVersion: GradleVersion
  get() = GradleVersion.current()


private operator fun GradleVersion.compareTo(version: String): Int =
  compareTo(GradleVersion.version(version))


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
  visible: Boolean = true
) {
  isCanBeResolved = false
  isCanBeConsumed = true
  canBeDeclared = false
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
internal fun Configuration.resolvable(
  visible: Boolean = false
) {
  isCanBeResolved = true
  isCanBeConsumed = false
  canBeDeclared = false
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
internal fun Configuration.declarable(
  visible: Boolean = false
) {
  isCanBeResolved = false
  isCanBeConsumed = false
  canBeDeclared = true
  isVisible = visible
}


@Suppress("UnstableApiUsage")
private var Configuration.canBeDeclared: Boolean
  get() = if (CurrentGradleVersion >= "8.2") {
    isCanBeDeclared
  } else {
    false
  }
  set(value) {
    if (CurrentGradleVersion >= "8.2") {
      isCanBeDeclared = value
    }
  }


//class x {
//  val Configuration.f: Boolean by
//  GradleVersionRequire2<Configuration, Boolean>(
//    Configuration::isCanBeDeclared,
//    Configuration::setCanBeDeclared
//  ){ it > "8.3"}
//}
//
//fun <REF: Any?, T : Any> GradleVersionRequire2(
//  getter: (REF) -> T,
//  setter: REF.(T?) -> Unit,
//  versionPredicate: (gradle: GradleVersion) -> Boolean,
//
//) = PropertyDelegateProvider { thisRef: Any?, property ->
//  GradleVersionRequire(getter, setter, versionPredicate)
//}


//class GradleVersionRequire<REF: Any?, T : Any>(
//  private val getter: (REF) -> T,
//  private val setter: REF.(T?) -> Unit,
//  private val versionPredicate: (gradle: GradleVersion) -> Boolean,
//) : ReadWriteProperty<REF, T?> {
//  override operator fun getValue(thisRef: REF, property: KProperty<*>): T? {
//    return if (versionPredicate(CurrentGradleVersion)) {
//      getter(thisRef)
//    } else {
//      null
//    }
//  }
//
//  override operator fun setValue(thisRef: REF, property: KProperty<*>, value: T?) {
//    if (versionPredicate(CurrentGradleVersion)) {
//      setter(thisRef, value)
//    }
//  }
//}


/** Drop the first [count] directories from the [RelativePath] */
internal fun RelativePath.dropDirectories(count: Int): RelativePath =
  RelativePath(true, *segments.drop(count).toTypedArray())


/** Drop the first directory from the [RelativePath] */
internal fun RelativePath.dropDirectory(): RelativePath =
  dropDirectories(1)


/** Instantiate a new [Attribute] of type [T] */
internal inline fun <reified T> Attribute(name: String): Attribute<T> = Attribute.of(name, T::class.java)


internal operator fun <T> AttributeContainer.get(key: Attribute<T>): T? =
  getAttribute(key)
