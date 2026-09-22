package dev.adamko.gradle.dev_publish.internal.checksums

import java.io.File
import java.io.InputStream
import java.io.OutputStream.nullOutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import kotlin.io.encoding.Base64

internal fun File.checksum(): String =
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
