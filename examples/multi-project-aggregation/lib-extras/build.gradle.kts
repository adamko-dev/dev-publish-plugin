plugins {
  kotlin("jvm")
  `maven-publish`
  id("dev.adamko.dev-publish")
}

group = "com.example"
version = "1.0.0"

dependencies {
  // `api`, so consumers of lib-extras compile against lib-core too.
  api(project(":lib-core"))

  // Shared with consumers: anything that devPublications lib-extras also gets lib-core.
  devPublication(project(":lib-core"))
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])
    }
  }
}
