# Multi-project aggregation

Several library projects publish Maven artifacts, and a separate `functional-tests` project collects
all of them into one dev Maven repository before running its tests.

Run it with:

```shell
gradle test
```

## Transitive dev publications

`functional-tests/build.gradle.kts` names only `lib-extras` and `signed-library`, yet `lib-core`
ends up in its dev repo too, because `lib-extras/build.gradle.kts` declares

```kotlin
devPublication(project(":lib-core"))
```

and `devPublication` dependencies are shared with consumers. Anyone declaring a `devPublication`
dependency on `lib-extras` also gets `lib-core`'s publications, without naming `lib-core`
themselves.

`DevRepoTest` asserts all three are present, and that `lib-core` arrives transitively.

## Signing without credentials

`signed-library/build.gradle.kts` applies the `signing` plugin and calls
`sign(publishing.publications)`, but configures no signatory
(the normal situation on a developer machine and in pull-request CI).

The tests still run, because the project conditionally disables signing:

```kotlin
signing {
  setRequired(publishingOutsideDevRepo)
}
```

Signing is only required when the build publishes somewhere other than the dev repo.
`DevRepoTest` asserts that no `.asc` files are published.
