plugins {
  kotlin("jvm")
  `maven-publish`
  id("dev.adamko.dev-publish")
}

group = "com.example"
version = "1.0.0"
description = "A library that does something."
// This project is tested by the `:functional-tests` project.

publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])
    }
  }
}
