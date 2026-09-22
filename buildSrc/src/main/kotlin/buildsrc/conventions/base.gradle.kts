package buildsrc.conventions

import java.time.Duration
import org.gradle.api.tasks.testing.logging.TestLogEvent.*

plugins {
  base
}

tasks.withType<AbstractTestTask>().configureEach {
  timeout.set(Duration.ofMinutes(60))

  testLogging {
    showCauses = true
    showExceptions = true
    showStackTraces = true
    showStandardStreams = true
    events(
      PASSED,
      FAILED,
      SKIPPED,
    )
  }
}

tasks.withType<AbstractCopyTask>().configureEach {
  includeEmptyDirs = false
}
