package dev.adamko.gradle.dev_publish.internal.checksums

import java.io.InputStream
import java.io.OutputStream.nullOutputStream
import java.nio.file.Path
import java.security.DigestOutputStream
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo

internal fun Path.checksum(): String =
  if (exists()) inputStream().checksum() else "missing"

private fun InputStream.checksum(): String {
  val md = MessageDigest.getInstance("SHA-256")

  buffered().use { input ->
    DigestOutputStream(nullOutputStream(), md).use { digestStream ->
      input.copyTo(digestStream)
    }
  }

  return Base64.encode(md.digest())
}

/**
 * SHA-256 hex of [files], by parent directory and content.
 *
 * File names are excluded, because SNAPSHOT releases rename each file with a timestamp and build number.
 */
internal fun repoChecksum(
  repoDir: Path,
  files: Iterable<Path>,
): String {
  val entries = files
    .map { file ->
      val relativeParentDir = file.parent.relativeTo(repoDir).invariantSeparatorsPathString
      "$relativeParentDir : ${file.checksum()} \u0000"
    }
    .sorted()
    .joinToString("\n")

  return MessageDigest.getInstance("SHA-256")
    .digest(entries.toByteArray())
    .toHexString()
}
