plugins {
  kotlin("jvm")
  `maven-publish`
  signing
  id("dev.adamko.dev-publish")
}

group = "com.example"
version = "1.0.0"

publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])
    }
  }
}

signing {
  sign(publishing.publications)

  // No signatory is configured, and the tests still run. See this example's README.
  setRequired(publishingOutsideDevRepo)
}
