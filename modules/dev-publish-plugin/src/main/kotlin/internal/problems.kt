@file:Suppress("UnstableApiUsage")

package dev.adamko.gradle.dev_publish.internal

import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.SIGNING__EXTERNAL_PUBLISHING_PROPERTY
import org.gradle.api.InvalidUserDataException
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemReporter

/** Root [ProblemGroup] for every problem that DevPublish reports. */
internal val DevPublishProblemGroup: ProblemGroup =
  ProblemGroup.create("dev-publish", "DevPublish")

internal val deprecatedTaskProblemId: ProblemId =
  ProblemId.create("deprecated-task", "Deprecated task", DevPublishProblemGroup)

internal val missingSignatoryProblemId: ProblemId =
  ProblemId.create("missing-signatory", "Missing signatory", DevPublishProblemGroup)

internal val signingExtensionNotExtensionAwareProblemId: ProblemId =
  ProblemId.create(
    "signing-extension-not-extension-aware",
    "Signing extension is not extension-aware",
    DevPublishProblemGroup,
  )

internal fun ProblemReporter.reportSigningExtensionNotExtensionAware() {
  report(signingExtensionNotExtensionAwareProblemId) {
    contextualLabel("The SigningExtension is not ExtensionAware")
    details(
      """
      |DevPublish adds a `$SIGNING__EXTERNAL_PUBLISHING_PROPERTY` property to the `signing {}`
      |block, which requires SigningExtension to implement the ExtensionAware interface.
      |
      |The property is not required, it is only a helper, the project will continue to work.
      |However, it is unexpected.
      |""".trimMargin()
    )
    solution("Use a Gradle version with an ExtensionAware SigningExtension.")
    solution("Report the issue to Gradle or https://github.com/adamko-dev/dev-publish-plugin.")
  }
}

/**
 * Fail [taskPath], explaining that it cannot sign a dev-repo publication and how to stop requiring
 * it.
 */
@Suppress("LocalVariableName")
internal fun ProblemReporter.failMissingSignatory(
  taskPath: String,
): Nothing {
  fun signingBlock(condition: String): String = "signing { setRequired$condition }"

  val `signing { setRequired(publishingOutsideDevRepo) }` =
    signingBlock("($SIGNING__EXTERNAL_PUBLISHING_PROPERTY)")
  val `signing { setRequired { someOtherCondition and publishingOutsideDevRepo } }` =
    signingBlock(" { someOtherCondition() || ${SIGNING__EXTERNAL_PUBLISHING_PROPERTY}.get() }")

  val `Only require signing when publishing elsewhere` =
    "Only require signing when publishing externally: $`signing { setRequired(publishingOutsideDevRepo) }`"

  throw throwing(InvalidUserDataException(), missingSignatoryProblemId) {
    contextualLabel("DevPublish cannot publish to the dev Maven repository if a signatory is required, but missing.")
    details(
      """
      |DevPublish tried to publish to the local dev Maven repo, but failed because the `signing` plugin is configured to require a signatory, but no signatory is available.
      |
      |To resolve this, conditionally disable signing when no external publishing tasks are running.
      |DevPublish adds a `$SIGNING__EXTERNAL_PUBLISHING_PROPERTY` property to the `signing {}` block to help:
      |
      |    $`signing { setRequired(publishingOutsideDevRepo) }`
      |
      |If the project already has its own condition, the conditions can be combined:
      |
      |    $`signing { setRequired { someOtherCondition and publishingOutsideDevRepo } }`
      |""".trimMargin()
    )
    solution(`Only require signing when publishing elsewhere`)
  }
}
