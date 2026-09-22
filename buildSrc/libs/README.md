# Bootstrap jars

This build uses the plugin it builds, so `buildSrc` needs DevPublish on its classpath before
DevPublish can be compiled. These jars break that cycle, and are committed for that reason.

Do not edit them by hand. After changing the plugin, or the version in
`buildSrc/src/main/kotlin/buildsrc/conventions/maven-publishing.gradle.kts`, run:

```shell
./gradlew updateBootstrap
```

and commit the result.
