package dev.adamko.gradle.dev_publish.tasks

import org.gradle.api.DefaultTask
import org.gradle.work.DisableCachingByDefault

/** Base task type for all [dev.adamko.gradle.dev_publish.DevPublishPlugin] tasks. */
@DisableCachingByDefault
abstract class BaseDevPublishTask : DefaultTask() {
  init {
    group = TASK_GROUP
  }

  companion object {
    const val TASK_GROUP = "dev publish"
  }
}
