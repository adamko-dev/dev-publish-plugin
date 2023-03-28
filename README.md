[![GitHub license](https://img.shields.io/github/license/adamko-dev/dev-publish-plugin?style=for-the-badge)](https://github.com/adamko-dev/dev-publish-plugin/blob/main/LICENSE)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/dev.adamko.dev-publish?logo=gradle&style=for-the-badge)](https://plugins.gradle.org/plugin/dev.adamko.dev-publish)

# Dev Publish Gradle Plugin

[Dev Publish](https://github.com/adamko-dev/dev-publish-plugin) is a [Gradle](https://gradle.org/) plugin
that publishes subprojects to a project-local directory, ready for functional testing.

### Why Dev Publish?

* Create a project local repository for testing only your publications
* Avoid using Maven Local
  (see: [*"you should avoid adding mavenLocal() as a
  repository"*](https://docs.gradle.org/current/userguide/declaring_repositories.html#sec:case-for-maven-local))
* Perfect for testing Gradle plugins and
  [Gradle Plugin Marker Artifacts](https://docs.gradle.org/current/userguide/plugins.html#sec:plugin_markers)
  (instead of using [TestKit + `withRuntimeClasspath()`](https://docs.gradle.org/8.0/userguide/test_kit.html#sub:test-kit-automatic-classpath-injection))
* Avoids unnecessary re-publishing (even if using SNAPSHOT versions)
* Compatible with Gradle Build and Configuration cache

### Quick Start

If a subproject already publishes Maven artifacts, then apply DevPublish using the plugin ID `dev.adamko.dev-publish`
and [the latest version](https://plugins.gradle.org/plugin/dev.adamko.dev-publish).

```kotlin
// build.gradle.kts

plugins {
  `maven-publish`
  id("dev.adamko.dev-publish") version "$devPublishPluginVersion>"
}
```

Any project can depend on publications from other subprojects

```kotlin
// build.gradle.kts
plugins {
  id("dev.adamko.dev-publish")
}

dependencies {
  devPublication(project(":some-other-subproject"))
}
```

#### Cross-project

Dev Publish is great for cross-project publications.

Given multiple subprojects:

```text
.
└── root/
    ├── my-java-library/    
    │   └── build.gradle.kts
    │
    ├── my-kotlin-application/
    │   └── build.gradle.kts
    │
    └── functional-tests/
        └── build.gradle.kts
```

Both the 'Java library' and 'Kotlin application' subprojects publish using the
[Maven Publish Plugin](https://docs.gradle.org/current/userguide/publishing_maven.html).

```kotlin
// my-java-library/build.gradle.kts
plugins {
  `java-library`
  `maven-publish`
  id("dev.adamko.dev-publish")
}
```

```kotlin
// my-kotlin-application/build.gradle.kts
plugins {
  application
  kotlin("jvm")
  `maven-publish`
  id("dev.adamko.dev-publish")
}
```

The 'functional-tests' subproject can aggregate the publications and collect them into a
project-local directory, ready for testing.

```kotlin
// functional-tests/build.gradle.kts
plugins {
  id("dev.adamko.dev-publish")
}

dependencies {
  devPublication(project(":my-java-library"))
  devPublication(project(":my-kotlin-application"))
}

tasks.test {
  // will publish all publications from 
  // :my-java-library and :my-kotlin-application
  // into the `devPublish.devMavenRepo` directory
  dependsOn(tasks.updateDevRepo)

  // provide devMavenRepo for use in tests
  environment(
    "MAVEN_DEV_REPO" to devPublish.devMavenRepo.asFile.get().invariantSeparatorsPath
  )
}
```
