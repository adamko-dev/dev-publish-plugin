[![GitHub license](https://img.shields.io/github/license/adamko-dev/dev-publish-plugin?style=for-the-badge)](https://github.com/adamko-dev/dev-publish-plugin/blob/main/LICENSE)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/dev.adamko.dev-publish?logo=gradle&style=for-the-badge)](https://plugins.gradle.org/plugin/dev.adamko.dev-publish)

# Dev Publish Gradle Plugin

[Dev Publish](https://github.com/adamko-dev/dev-publish-plugin) is a [Gradle](https://gradle.org/) plugin
that supports functional testing of a published module.

Each subproject is published to a local file-based Maven repository, containing real publications
(Gradle Module Metadata, Plugin Marker Artifacts, checksums) that your tests can resolve.

* A project-local repository for testing only your publications, instead of Maven Local.\
  [*"You should avoid adding mavenLocal () as a repository"*](https://docs.gradle.org/9.7.1/userguide/supported_repository_types.html#sec:case-for-maven-local).
* Perfect for testing Gradle plugins and
  [Plugin Marker Artifacts](https://docs.gradle.org/9.7.1/userguide/plugins_intermediate.html#sec:plugin_markers),
  instead of [TestKit +
  `withRuntimeClasspath()`](https://docs.gradle.org/9.7.1/userguide/test_kit.html#sub:test-kit-automatic-classpath-injection).
* Avoids unnecessary re-publishing - a content checksum means nothing is republished unless it
  actually changed, even for SNAPSHOT versions.
* Supports Gradle Build Cache, Configuration Cache, and Isolated Projects.

## Requirements

* Gradle: 8.14.5+
* Gradle JVM: 17+
* Kotlin: 2.1+, to compile tests that use `dev-publish-utils`.
  A Gradle plugin project uses Gradle's embedded Kotlin, so it needs Gradle 9+.
* Required plugins: [`maven-publish`](https://docs.gradle.org/9.7.1/userguide/publishing_maven.html), in every project
  that publishes.

## Quick Start

Apply `maven-publish` and Dev Publish to any subproject that publishes Maven artifacts, using the
plugin ID `dev.adamko.dev-publish` and
[the latest version](https://plugins.gradle.org/plugin/dev.adamko.dev-publish).

```kotlin
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
```

> See the full code
> [here](examples/multi-project-aggregation/lib-core/build.gradle.kts).

Any project can collect publications from other subprojects into its own dev repo, by declaring a
`devPublication(project("..."))` dependency.

```kotlin
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
```

> See the full code
> [here](examples/multi-project-aggregation/functional-tests/build.gradle.kts).

Declaring `devPublish.dependency()` runs `updateDevRepo` before the tests, puts the dev Maven
repository location on their runtime classpath, and adds `dev-publish-utils` for reading it.

`devPublication` dependencies are shared with consumers. If `:lib-extras` declares
`devPublication(project(":lib-core"))`, then anyone declaring `devPublication(project(":lib-extras"))`
gets `:lib-core`'s publications too, without naming `:lib-core`.

That is the default because a dev repo is only useful if it is closed. `:lib-extras`' published POM
and Gradle Module Metadata name `:lib-core`, so a test resolving `:lib-extras` from the dev repo
fails unless `:lib-core` is there as well. This applies to `implementation` dependencies too - they
appear in the POM at runtime scope.

To collect a project without its own `devPublication` dependencies, make the dependency
non-transitive:

```kotlin
dependencies {
  devPublication(project(":lib-extras")) { isTransitive = false }
}
```

This prunes every level at once, so anything else that is still needed must be named explicitly.

See the [`multi-project-aggregation` example](examples/multi-project-aggregation).

> [!IMPORTANT]
> Every project named in a `devPublication` dependency must itself apply Dev Publish. Declaring a
> dependency on a project that does not will fail with a variant-resolution error.
> `subprojects { devPublication(it) }` and `allprojects { devPublication(it) }` is not supported.

DevPublish will automatically update the dev repository to a local, isolated directory before running `gradle test`.

## Using the dev repo in tests

Once `devPublish.dependency()` is configured use the `dev-publish-utils` helper library in your tests.

```kotlin
@Test
fun `dev repo contains requested publications`() {
  val devRepo: Path = devMavenRepo()

  assertFileExists(devRepo.resolve("com/example/lib-extras/1.0.0/lib-extras-1.0.0.jar"))
  assertFileExists(devRepo.resolve("com/example/signed-library/1.0.0/signed-library-1.0.0.jar"))

  // lib-core is only reachable via lib-extras' devPublication dependency
  assertFileExists(devRepo.resolve("com/example/lib-core/1.0.0/lib-core-1.0.0.jar"))
}
```

> See the full code
> [here](examples/multi-project-aggregation/functional-tests/src/test/kotlin/com/example/tests/DevRepoTest.kt).

### Included builds

For a project in an [included build](https://docs.gradle.org/9.7.1/userguide/composite_builds.html),
use module notation rather than `project(...)`:

```kotlin
dependencies {
  devPublication("com.example:data-model")
}
```

## Testing a Gradle plugin with TestKit

Because the dev repo contains the real publication (including the
[Plugin Marker Artifact](https://docs.gradle.org/9.7.1/userguide/plugins_intermediate.html#sec:plugin_markers))
the build under test can apply your plugin by id and version, exactly as a user would, instead of
using `withPluginClasspath()`.

`devPublish.dependency()` also brings in the helpers that write the build under test's
`settings.gradle.kts`:

```kotlin
@Test
fun `plugin can be resolved by id and version`(@TempDir projectDir: Path) {
  // Writes a settings file that resolves the plugin under test from the dev Maven repo.
  projectDir.resolve("settings.gradle.kts").writeText(
    devPublishSettings(rootProjectName = "greeting-consumer")
  )

  // ...
}
```

> See the full code
> [here](examples/gradle-plugin-testkit/src/functionalTest/kotlin/com/example/greeting/GreetingPluginFunctionalTest.kt).

For finer control, `devPublishRepository()`, `devPublishPluginManagement()` and
`devPublishDependencyResolutionManagement()` return the individual `maven(devPublishRepo())` blocks.

See the [`gradle-plugin-testkit` example](examples/gradle-plugin-testkit).

## Signing

The
[`signing` plugin](https://docs.gradle.org/9.7.1/userguide/signing_plugin.html)
attaches signatures to a `MavenPublication`, not to an individual repository, so signatures cannot be
filtered out per-repository. A project that signs its publications therefore needs a GPG key just to
run its functional tests.

To avoid that, only require signing when the build is actually publishing somewhere else
by using `signing { setRequired(...) }`.
DevPublish adds a helper property, `publishingOutsideDevRepo`, to the `signing {}` block to help:

```kotlin
signing {
  setRequired(publishingOutsideDevRepo)
}
```

Combine it with your own conditions if you have them:

```kotlin
val signingCredentialsPresent: Property<Boolean> = /*...*/

  signing {
  setRequired { publishingOutsideDevRepo.get() || signingCredentialsPresent.get() }
}
```

## Examples

Example projects are available in the [`examples/`](examples) directory.
