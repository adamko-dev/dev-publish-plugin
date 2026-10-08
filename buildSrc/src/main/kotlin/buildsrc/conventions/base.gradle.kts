package buildsrc.conventions

import java.time.Duration
import org.gradle.api.tasks.testing.logging.TestLogEvent.*

plugins {
  base
}

tasks.withType<AbstractTestTask>().configureEach {
  timeout.set(Duration.ofMinutes(60))

  testLogging {
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
