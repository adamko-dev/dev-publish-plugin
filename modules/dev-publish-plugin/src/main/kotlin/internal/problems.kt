package dev.adamko.gradle.dev_publish.internal

import org.gradle.api.problems.ProblemGroup

/** Root [ProblemGroup] for every problem that DevPublish reports. */
@Suppress("UnstableApiUsage")
internal val DevPublishProblemGroup: ProblemGroup =
  ProblemGroup.create("dev-publish", "DevPublish")