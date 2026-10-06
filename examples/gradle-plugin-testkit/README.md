# Testing a Gradle plugin with TestKit

A Gradle plugin is published to a dev Maven repository, and a TestKit test applies it by plugin id
and version, exactly as a real user would, instead of using `withPluginClasspath()`.

Run it with:

```shell
gradle check
```

## Writing the settings file of the build under test

`GreetingPluginFunctionalTest` calls `devPublishSettings(rootProjectName = …)`, which returns a
complete `settings.gradle.kts` pointing at the dev repo.

The groups it filters on are derived from what is actually in the repo, so the Plugin Marker
Artifact (published under the plugin id, `com.example.greeting`) and the plugin implementation
(published under the project's group) are both covered without either being named by hand.

For finer control, `devPublishRepository()` returns just the `exclusiveContent { }` block, and
`devPublishPluginManagement()` / `devPublishDependencyResolutionManagement()` return the individual
blocks to drop into a settings file of your own.
