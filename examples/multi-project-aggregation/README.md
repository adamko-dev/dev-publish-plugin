# Multi-project aggregation

Several library projects publish Maven artifacts, and a separate `functional-tests` project collects
all of them into one dev Maven repository before running its tests.

Run it with:

```shell
gradle test
```

## `devPublication` vs `devPublicationApi`

`functional-tests/build.gradle.kts` names only `lib-extras` and `signed-library`, yet `lib-core`
ends up in its dev repo too. That is `devPublicationApi` at work: `lib-extras/build.gradle.kts`
declares

```kotlin
devPublicationApi(project(":lib-core"))
```

so anyone declaring a `devPublication` dependency on `lib-extras` also gets `lib-core`'s
publications, without naming `lib-core` themselves - exactly as `api` shares a compile dependency
with consumers, and `implementation` does not.

`DevRepoTest` asserts all three are present, and that `lib-core` arrives transitively.

## Signing without credentials

`signed-library/build.gradle.kts` applies the `signing` plugin and calls
`sign(publishing.publications)`, but configures no signatory - the normal situation on a developer
machine and in pull-request CI. The tests still run, because DevPublish skips `Sign` tasks in builds
that only publish to the dev repo. `DevRepoTest` asserts that no `.asc` files are published.
