package buildsrc.conventions

import java.time.Duration
import org.gradle.api.tasks.testing.logging.TestLogEvent.*

plugins {
  base
}

// common config for all projects

if (project != rootProject) {
  project.version = rootProject.version
  project.group = rootProject.group
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
      STARTED,
      STANDARD_ERROR,
      STANDARD_OUT,
    )
  }
}

tasks.withType<AbstractCopyTask>().configureEach {
  includeEmptyDirs = false
}
