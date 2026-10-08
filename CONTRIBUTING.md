# Contributing

## Prerequisites

* JDK 17 and JDK 21 available (the project compiles with a Java 17 toolchain, and the Gradle daemon
  uses JDK 21 - see `gradle/gradle-daemon-jvm.properties`).

## Running the checks

```shell
./gradlew check
```

## Examples

Each directory in `examples/` is a standalone Gradle build, and is built by `ExamplesTest` as part
of `gradle check`.

Examples are discovered automatically by `ExamplesTest`, so a new example is covered as soon as it
is added. Each example needs a `README.md` that describes it and shows the command to run, in a
`shell` code block:

````
```shell
gradle check
```
````

## README snippets

Every code snippet in the README comes from one of the runnable projects in `examples/`, and
`ReadmeSnippetsTest` checks that it still appears there. Follow the fenced code block with a quote
linking to the file it came from:

````markdown
```kotlin
dependencies {
  devPublication(project(":lib-extras"))
}
```

> See the full code [here](examples/some-example/build.gradle.kts).
````

Only the `[here](...)` link is read, so the quote could wrap over several lines.

The snippet must be a contiguous run of lines from that file, ignoring indentation and the
Dev Publish version. A `// ...` line marks lines that were left out; each part must appear somewhere
in the file. Nothing marks the region in the example itself: the examples are published for people to
read as-is, so they are kept free of tags that only mean something to this test.

## Bootstrap

This build uses DevPublish to test DevPublish. The version it uses is pinned by the
`devPublishBootstrap-*` libraries in `gradle/libs.versions.toml`.

The pinned versions are timestamped snapshots, which Maven Central deletes after 90 days. To update
them, copy the `<value>` of each module's latest `jar` from its `maven-metadata.xml`, for example
https://central.sonatype.com/repository/maven-snapshots/dev/adamko/gradle/dev-publish-plugin/1.2.0-SNAPSHOT/maven-metadata.xml.
The build number differs per module.

## Releasing

1. Update `version` in `gradle.properties`, and the Dev Publish version pinned in `examples/`
   (`ExamplesTest` checks they match).
2. Merge to `main`, `.github/workflows/workflow_release.yml` publishes.
