# Dev Publish examples

Each directory is a self-contained Gradle build that demonstrates one way of using
[Dev Publish](../README.md), and is built on every CI run by
[`ExamplesTest`](../modules/dev-publish-plugin/src/testIntegration/kotlin/ExamplesTest.kt).

- [`multi-project-aggregation`](multi-project-aggregation): Collecting several subprojects' publications into one dev
  repo, transitive `devPublication` dependencies, and publishing alongside the `signing` plugin.
- [`gradle-plugin-testkit`](gradle-plugin-testkit): Testing a Gradle plugin with TestKit, resolving it by plugin id and
  version instead of `withPluginClasspath()`.

Each example's own `README.md` declares the tasks to run.
