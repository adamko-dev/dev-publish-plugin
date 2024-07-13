package dev.adamko.gradle.dev_publish.internal.checksums

import java.io.File
import java.io.InputStream
import java.io.OutputStream.nullOutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.*

internal fun File.checksum(): String =
  if (exists()) inputStream().checksum() else "missing"

private fun InputStream.checksum(): String {
  val messageDigester = MessageDigest.getInstance("SHA-256")

  buffered().use { input ->
    DigestOutputStream(nullOutputStream(), messageDigester).use { digestStream ->
      input.copyTo(digestStream)
    }
  }

  return Base64.getEncoder().encodeToString(messageDigester.digest())
}
