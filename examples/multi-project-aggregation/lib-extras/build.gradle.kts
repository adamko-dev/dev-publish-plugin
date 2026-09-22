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

  // `devPublicationApi` is to `devPublication` what `api` is to `implementation`.
  devPublicationApi(project(":lib-core"))
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])
    }
  }
}
