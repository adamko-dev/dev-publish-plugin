package dev.adamko.gradle.dev_publish.utils

import org.gradle.api.logging.Logger

internal fun Logger.info(message: () -> String) {
  if (isInfoEnabled) {
    info(message())
  }
}
