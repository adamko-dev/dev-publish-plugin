package dev.adamko.gradle.dev_publish.internal

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.text.Charsets.UTF_8

class DevPublishMetadataTest : FunSpec({

  fun roundTrip(devMavenRepo: String): String? {
    val file = tempdir().toPath().resolve("nested/dev-publish.properties")
    DevPublishMetadata.write(file, devMavenRepo)
    return DevPublishMetadata.read(file)
  }

  test("expect a plain path round-trips") {
    roundTrip("../../maven-dev") shouldBe "../../maven-dev"
  }

  test("expect a non-ASCII path round-trips") {
    roundTrip("../café/日本/maven-dev") shouldBe "../café/日本/maven-dev"
  }

  test("expect characters that are special in a properties file round-trip") {
    roundTrip("""..\a=b:c#d!e	f""") shouldBe """..\a=b:c#d!e	f"""
  }

  test("expect a leading space round-trips") {
    roundTrip(" spaced/maven-dev") shouldBe " spaced/maven-dev"
  }

  test("expect the file is written as UTF-8, with no timestamp") {
    val file = tempdir().toPath().resolve("dev-publish.properties")
    DevPublishMetadata.write(file, "../café")

    file.readText(UTF_8) shouldBe "devMavenRepo=../café\n"
  }

  test("expect reading a file without the key returns null") {
    val file = tempdir().toPath().resolve("dev-publish.properties")
    file.writeText("somethingElse=1\n")

    DevPublishMetadata.read(file).shouldBeNull()
  }
})

private fun DevPublishMetadata.read(file: Path): String? =
  file.inputStream().use { input ->
    read(input)
  }
