plugins {
  kotlin("jvm")
  id("dev.adamko.dev-publish")
}

dependencies {
  devPublication(project(":lib-extras"))
  devPublication(project(":signed-library"))

  testImplementation(kotlin("test"))

  // Makes the `test` source set depend on dev publishing.
  testImplementation(devPublish.dependency())
}

tasks.test {
  useJUnitPlatform()
}
