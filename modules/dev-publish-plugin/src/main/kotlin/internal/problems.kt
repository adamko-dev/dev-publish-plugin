@file:Suppress("UnstableApiUsage")

package dev.adamko.gradle.dev_publish.internal

import dev.adamko.gradle.dev_publish.DevPublishPlugin.Companion.SIGNING__EXTERNAL_PUBLISHING_PROPERTY
import java.io.File
import org.gradle.api.InvalidUserDataException
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemReporter

/** Root [ProblemGroup] for every problem that DevPublish reports. */
internal val DevPublishProblemGroup: ProblemGroup =
  ProblemGroup.create("dev-publish", "DevPublish")

internal val deprecatedTaskProblemId: ProblemId =
  ProblemId.create("deprecated-task", "Deprecated task", DevPublishProblemGroup)

internal val devMavenRepoNotRelativeProblemId: ProblemId =
  ProblemId.create(
    "dev-maven-repo-not-relative",
    "Dev Maven repository has no relative path",
    DevPublishProblemGroup,
  )

internal val missingSignatoryProblemId: ProblemId =
  ProblemId.create("missing-signatory", "Missing signatory", DevPublishProblemGroup)

internal val publicationNotSetProblemId: ProblemId =
  ProblemId.create("publication-not-set", "Publication not set", DevPublishProblemGroup)

internal val signingExtensionNotExtensionAwareProblemId: ProblemId =
  ProblemId.create(
    "signing-extension-not-extension-aware",
    "Signing extension is not extension-aware",
    DevPublishProblemGroup,
  )

/**
 * Fail because the dev Maven repository has no path relative to the metadata directory, so its
 * location cannot be recorded.
 *
 * Thrown rather than reported, because the metadata file cannot be written at all.
 */
internal fun ProblemReporter.failDevMavenRepoNotRelative(
  devMavenRepo: File,
  metadataDir: File,
): Nothing {
  val setDevMavenRepo =
    "Set devPublish.devMavenRepo to a location under the same root as the build directory."

  throw throwing(InvalidUserDataException(), devMavenRepoNotRelativeProblemId) {
    contextualLabel("The dev Maven repository has no path relative to the metadata directory")
    details(
      """
      |DevPublish records the dev Maven repository's location relative to the metadata file, so the
      |recorded path is the same on every machine and checkout, and the test tasks that read it stay
      |relocatable for the build cache.
      |
      |    dev Maven repository: ${devMavenRepo.invariantSeparatorsPath}
      |    metadata directory:   ${metadataDir.invariantSeparatorsPath}
      |
      |There is no relative path between these two, so the location cannot be recorded.
      |""".trimMargin()
    )
    solution(setDevMavenRepo)
  }
}

/**
 * Warn that a publishing task has no [org.gradle.api.publish.maven.MavenPublication], so DevPublish
 * cannot record it.
 *
 * Reported, not thrown: the publication is skipped and the rest of the build still works.
 */
internal fun ProblemReporter.reportPublicationNotSet() {
  report(publicationNotSetProblemId) {
    contextualLabel("Publishing task has no MavenPublication")
    details(
      """
      |DevPublish records each Maven publication, so it can tell when one has changed and must be
      |re-published to the dev Maven repository. A publishing task with no publication cannot be
      |recorded, and is skipped, so the DevPublish Maven repository may be missing artifacts.
      |
      |A publishing task normally always has a publication, so this is unexpected.
      |""".trimMargin()
    )
    solution("Check for a publishing task that was created without a publication.")
    solution("Report the issue to https://github.com/adamko-dev/dev-publish-plugin.")
  }
}

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
