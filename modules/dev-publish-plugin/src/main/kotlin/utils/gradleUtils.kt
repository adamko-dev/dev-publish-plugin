package dev.adamko.gradle.dev_publish.utils

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.RelativePath
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.invoke
import org.gradle.util.GradleVersion


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
internal inline fun <reified T : Any> Attribute(name: String): Attribute<T> =
  Attribute.of(name, T::class.java)


//internal operator fun <T : Any> AttributeContainer.get(key: Attribute<T>): T? =
//  getAttribute(key)

/** Calls [DefaultTask.onlyIf], but smart-casts the task. */
@Suppress("FunctionName")
internal inline fun <reified T : DefaultTask> T.onlyIf_(
  reason: String,
  crossinline spec: T.() -> Boolean,
) {
  onlyIf(reason) { task ->
    require(task is T) { "invalid task type in onlyIf. Expected ${T::class}, but was ${this::class}." }
    task.spec()
  }
}

/** Calls [Task.doFirst], but smart-casts the task. */
@Suppress("FunctionName")
internal inline fun <reified T : Task> T.doFirst_(
  name: String,
  action: Action<T>,
) {
  doFirst(name) {
    require(this is T) { "invalid task type in doFirst. Expected ${T::class}, but was ${this::class}." }
    action(this)
  }
}


/** Calls [Task.doLast], but smart-casts the task. */
@Suppress("FunctionName")
internal inline fun <reified T : Task> T.doLast_(
  name: String,
  action: Action<T>,
) {
  doLast(name) {
    require(this is T) { "invalid task type in doLast. Expected ${T::class}, but was ${this::class}." }
    action(this)
  }
}


@Suppress("FunctionName")
internal fun Configuration.extendsFrom_(configuration: Provider<out Configuration>) {
  if (CurrentGradleVersion >= "9.4.0") {
    @Suppress("UnstableApiUsage")
    extendsFrom(configuration)
  } else {
    extendsFrom(configuration.get())
  }
}

/**
 * Create a [ProjectDependency] for [project].
 */
internal fun createProjectDependency(project: Project): ProjectDependency {
  return if (CurrentGradleVersion >= "9.5.0") {
    project.dependencies.project()
  } else {
    project.dependencies.create(project) as? ProjectDependency
      ?: error("Failed to create project dependency for project ${project.path}. Current Gradle version: $CurrentGradleVersion")
  }
}

internal val Project.groupProvider: Provider<String>
  get() = providers.provider { group.toString() }
