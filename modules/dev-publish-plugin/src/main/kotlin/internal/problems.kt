@file:Suppress("UnstableApiUsage")

package dev.adamko.gradle.dev_publish.internal

import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId

/** Root [ProblemGroup] for every problem that DevPublish reports. */
internal val DevPublishProblemGroup: ProblemGroup =
  ProblemGroup.create("dev-publish", "DevPublish")

internal val deprecatedTaskProblemId: ProblemId =
  ProblemId.create("deprecated-task", "Deprecated task", DevPublishProblemGroup)
