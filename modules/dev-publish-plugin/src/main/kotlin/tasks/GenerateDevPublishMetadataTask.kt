@file:Suppress("UnstableApiUsage")

package dev.adamko.gradle.dev_publish.tasks

import dev.adamko.gradle.dev_publish.internal.DevPublishInternalApi
import dev.adamko.gradle.dev_publish.internal.DevPublishMetadata.REPO_CHECKSUM_FILE_NAME
import dev.adamko.gradle.dev_publish.internal.DevPublishMetadata.REPO_LOCATION_FILE_NAME
import dev.adamko.gradle.dev_publish.internal.checksums.checksum
import dev.adamko.gradle.dev_publish.internal.failDevMavenRepoNotRelative
import dev.adamko.gradle.dev_publish.tasks.GenerateDevPublishMetadataTask.Companion.repoPath
import dev.adamko.gradle.dev_publish.utils.nullOutputStream
import java.nio.file.Path
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.stream.Collectors
import javax.inject.Inject
import kotlin.io.path.*
import kotlin.text.Charsets.UTF_8
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.problems.ProblemReporter
import org.gradle.api.problems.Problems
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import org.gradle.api.tasks.PathSensitivity.NONE
import org.gradle.work.ChangeType.REMOVED
import org.gradle.work.FileChange
import org.gradle.work.Incremental
import org.gradle.work.InputChanges

/**
 * Writes the metadata files used at runtime to access the repository.
 *
 * Produces two files with content:
 * 1. The relative location of the dev Maven repository, read at runtime by `dev-publish-utils`.
 * 2. A hash of the files in the dev Maven repository, for up-to-date checks.
 *
 * @see dev.adamko.gradle.dev_publish.internal.DevPublishMetadata.REPO_LOCATION_FILE_NAME
 * @see dev.adamko.gradle.dev_publish.internal.DevPublishMetadata.REPO_CHECKSUM_FILE_NAME
 */
@CacheableTask
abstract class GenerateDevPublishMetadataTask
@Inject
@DevPublishInternalApi
constructor(
  problems: Problems,
) : BaseDevPublishTask() {

  private val problemsReporter: ProblemReporter = problems.reporter

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  /** @see dev.adamko.gradle.dev_publish.DevPublishPluginExtension.devMavenRepo */
  @get:Internal
  abstract val devMavenRepo: DirectoryProperty

  /**
   * Every file in [devMavenRepo] that is tracked as a task input.
   *
   * Metadata, checksums, and signatures are excluded,
   * because they are not deterministic (`maven-metadata.xml`, contains timestamps),
   * or are not relevant (Dev Publish recomputes the checksum of artifacts, so checksums can be ignored).
   *
   * Must only contain files from [devMavenRepo].
   * Adding multiple directories could result in clashes, because files are keyed by relative paths.
   *
   * @see REPO_CHECKSUM_FILE_NAME
   */
  @get:Incremental
  @get:InputFiles
  @get:PathSensitive(NONE) // file names might include SNAPSHOT timestamps
  @get:IgnoreEmptyDirectories
  @DevPublishInternalApi
  abstract val devMavenRepoTrackedFiles: ConfigurableFileCollection

  @get:Input
  @get:Optional
  internal val devMavenRepoTrackedFilesPaths: Provider<Set<String>>
    get() {
      return devMavenRepo.zip(devMavenRepoTrackedFiles.elements) { devMavenRepoDir, trackedFiles ->
        val devMavenRepo = devMavenRepoDir.asFile.toPath()
        trackedFiles
          .map { f -> f.asFile.toPath() }
          .filter { f -> f.isRegularFile() }
          .map { f -> repoPath(devMavenRepo, f) }
          .toSortedSet()
      }
    }

  /**
   * @see dev.adamko.gradle.dev_publish.DevPublishPluginExtension.excludedDevMavenRepoFilePatterns
   */
  @get:Internal
  @DevPublishInternalApi
  abstract val excludedDevMavenRepoFilePatterns: SetProperty<String>

  /** Location of the dev Maven repository, relative to [outputDirectory]. */
  @get:Input
  val devMavenRepoRelativeToMetadataDir: Provider<String>
    get() = devMavenRepo.zip(outputDirectory.locationOnly) { devMavenRepo, metadataDir ->
      val baseDir = metadataDir.asFile.resolve(REPO_LOCATION_FILE_NAME).normalize().parentFile
      val repoDir = devMavenRepo.asFile
      val relative = repoDir.relativeToOrNull(baseDir)
        ?: problemsReporter.failDevMavenRepoNotRelative(
          devMavenRepo = repoDir,
          metadataDir = baseDir,
        )
      relative.invariantSeparatorsPath
    }

  /**
   * Stores the cache key of each file in [devMavenRepoTrackedFiles] in a single file,
   * for incremental checksum computation.
   */
  @get:LocalState
  @DevPublishInternalApi
  abstract val stateDir: DirectoryProperty

  @TaskAction
  @DevPublishInternalApi
  protected fun generate(inputChanges: InputChanges) {
    val outputDir = outputDirectory.get().asFile.toPath()

    // delete output dir first, to clear any stale files
    outputDir.deleteRecursively()
    outputDir.createDirectories()

    outputDir.resolve(REPO_LOCATION_FILE_NAME)
      .writeText(devMavenRepoRelativeToMetadataDir.get())

    val checksum = computeChecksum(inputChanges)
    outputDir.resolve(REPO_CHECKSUM_FILE_NAME)
      .writeText(checksum)
  }

  private fun computeChecksum(inputChanges: InputChanges): String {
    val stateDir = stateDir.get().asFile.toPath()
    val stateFile = stateDir.resolve("checksums.dat")

    if (!inputChanges.isIncremental) {
      stateDir.deleteRecursively()
    }
    stateDir.createDirectories()

    // repoPath -> cache value
    val state: MutableMap<String, String> = readStateEntries(stateFile)

    updateState(
      state = state,
      fileChanges = inputChanges.getFileChanges(devMavenRepoTrackedFiles),
      devMavenRepo = devMavenRepo.get().asFile.toPath(),
    )

    writeStateEntries(stateFile, state)

    val md = MessageDigest.getInstance("SHA-256")
    DigestOutputStream(nullOutputStream(), md)
      .bufferedWriter(UTF_8)
      .use { digestStream ->
        state.values
          .sorted()
          .forEach { cacheValue ->
            digestStream.write(cacheValue)
            digestStream.write(CHECKSUM_CACHE_VALUE_SEPARATOR)
          }
      }
    return md.digest().toHexString()
  }

  companion object {

    /** Separates the normalized path from the cache value, in a state file line. */
    private const val STATE_FILE_PATH_SEPARATOR = '\t'

    /** Ends each line in the state file. */
    private const val STATE_FILE_LINE_SEPARATOR = '\n'

    /** Separates cache values in the repo checksum input, so concatenated values can't be ambiguous. */
    private const val CHECKSUM_CACHE_VALUE_SEPARATOR = "\n"

    /** The timestamp and build number in a SNAPSHOT file name, e.g. `-20261113.215135-1`. */
    private val SNAPSHOT_TIMESTAMP = Regex("""-\d{8}\.\d{6}-\d+""")

    /**
     * Path of [file] relative to [devMavenRepo], with any SNAPSHOT timestamp replaced by `-SNAPSHOT`,
     * so re-publishing a SNAPSHOT doesn't change it.
     */
    private fun repoPath(devMavenRepo: Path, file: Path): String {
      val normalizedFile =
        if (file.parent?.name?.endsWith("-SNAPSHOT") == true) {
          file.resolveSibling(file.name.replace(SNAPSHOT_TIMESTAMP, "-SNAPSHOT"))
        } else {
          file
        }
      return normalizedFile.relativeTo(devMavenRepo).invariantSeparatorsPathString
    }

    /** `parentDir:checksum`. The file name is excluded, because SNAPSHOT publishing renames files. */
    private fun computeCacheValue(file: Path, repoPath: String): String {
      val parentDir = Path(repoPath).parent
        ?.invariantSeparatorsPathString
        ?: "<root>"
      return "${parentDir}:${file.checksum()}"
    }


    /** Read lines of `repoPath<TAB>cacheValue`. */
    private fun readStateEntries(stateFile: Path): MutableMap<String, String> {
      if (!stateFile.exists()) {
        return mutableMapOf()
      }

      return stateFile.useLines(UTF_8) { lines ->
        lines
          .filter { it.isNotBlank() }
          .associateTo(mutableMapOf()) { line ->
            val parts = line.split(STATE_FILE_PATH_SEPARATOR, limit = 2)
            require(parts.size == 2) { "Invalid state file line `$line` in ${stateFile.invariantSeparatorsPathString}" }
            parts.run { first() to last() }
          }
      }
    }

    /** Write lines of `repoPath<TAB>cacheValue`. */
    private fun writeStateEntries(stateFile: Path, state: Map<String, String>) {
      stateFile.writeText(
        state.entries.joinToString("") { (repoPath, cacheValue) ->
          buildString {
            append(repoPath)
            append(STATE_FILE_PATH_SEPARATOR)
            append(cacheValue)
            append(STATE_FILE_LINE_SEPARATOR)
          }
        },
        UTF_8,
      )
    }

    /**
     * Keyed by [repoPath], not [FileChange.getNormalizedPath],
     * because [devMavenRepoTrackedFiles] ignores paths, so its normalised paths are empty.
     */
    private fun updateState(
      state: MutableMap<String, String>,
      fileChanges: Iterable<FileChange>,
      devMavenRepo: Path,
    ) {
      val (removalChanges, modificationChanges) =
        fileChanges.partition { it.changeType == REMOVED }

      removalChanges.forEach { state.remove(repoPath(devMavenRepo, it.file.toPath())) }

      modificationChanges
        .parallelStream()
        .map { change ->
          val file = change.file.toPath()
          val repoPath = repoPath(devMavenRepo, file)
          repoPath to computeCacheValue(file, repoPath)
        }
        .collect(Collectors.toList())
        .forEach { (repoPath, cacheValue) ->
          state[repoPath] = cacheValue
        }
    }
  }
}
