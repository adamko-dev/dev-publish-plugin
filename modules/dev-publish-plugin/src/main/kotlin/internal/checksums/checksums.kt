package dev.adamko.gradle.dev_publish.internal.checksums

import dev.adamko.gradle.dev_publish.utils.nullOutputStream
import java.io.InputStream
import java.nio.file.Path
import java.security.DigestOutputStream
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.path.exists
import kotlin.io.path.inputStream

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
