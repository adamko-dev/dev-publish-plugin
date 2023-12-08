package dev.adamko.gradle.dev_publish.utils

import org.gradle.kotlin.dsl.support.useToRun
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream.nullOutputStream
import java.math.BigInteger
import java.security.DigestOutputStream
import java.security.MessageDigest

internal fun File.md5(): String = if (exists()) inputStream().md5() else "missing"

private fun InputStream.md5(): String {
  val md5 = messageDigestMd5()

  object : Closeable {
    val fileStream = buffered()
    val digestStream = DigestOutputStream(nullOutputStream(), md5)

    override fun close() {
      fileStream.close()
      digestStream.close()
    }
  }.useToRun {
    fileStream.copyTo(digestStream)
  }

  return "%032x".format(BigInteger(1, md5.digest()))
}

private fun messageDigestMd5(): MessageDigest = MessageDigest.getInstance("MD5")
