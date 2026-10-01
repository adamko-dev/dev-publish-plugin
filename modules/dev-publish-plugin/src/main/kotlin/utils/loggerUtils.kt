package dev.adamko.gradle.dev_publish.utils

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.LogLevel.*
import org.gradle.api.logging.Logger

internal fun Logger.debug(throwable: Throwable? = null, message: () -> String): Unit =
  log(level = DEBUG, message = message, throwable = throwable)

internal fun Logger.lifecycle(throwable: Throwable? = null, message: () -> String): Unit =
  log(level = LIFECYCLE, message = message, throwable = throwable)

//internal fun Logger.quiet(throwable: Throwable? = null, message: () -> String): Unit =
//  log(level = QUIET, message = message, throwable = throwable)

//internal fun Logger.warn(throwable: Throwable? = null, message: () -> String): Unit =
//  log(level = WARN, message = message, throwable = throwable)

internal fun Logger.info(throwable: Throwable? = null, message: () -> String): Unit =
  log(level = INFO, message = message, throwable = throwable)

//internal fun Logger.error(throwable: Throwable? = null, message: () -> String): Unit =
//  log(level = ERROR, message = message, throwable = throwable)

internal fun Logger.log(
  level: LogLevel,
  message: () -> String,
  throwable: Throwable? = null,
) {
  if (isEnabled(level)) {
    if (throwable != null) {
      log(level, message(), throwable)
    } else {
      log(level, message())
    }
  }
}
