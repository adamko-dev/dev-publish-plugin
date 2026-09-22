plugins {
  kotlin("jvm") version embeddedKotlinVersion
  `java-gradle-plugin`
  `jvm-test-suite`
  `maven-publish`
  id("dev.adamko.dev-publish") version "1.1.0"
}

group = "com.example"
version = "1.0.0"

gradlePlugin {
  // `isAutomatedPublishing` (the default) also publishes the Plugin Marker Artifact, which is what
  // lets a TestKit build resolve this plugin with `id("com.example.greeting") version "1.0.0"`.
  plugins.register("greeting") {
    id = "com.example.greeting"
    implementationClass = "com.example.greeting.GreetingPlugin"
  }
}

testing.suites {
  register<JvmTestSuite>("functionalTest") {
    useKotlinTest(embeddedKotlinVersion)

    dependencies {
      implementation(gradleTestKit())

      // Makes this suite, rather than `test`, depend on dev publishing.
      implementation(devPublish.dependency())
    }
  }
}

tasks.check {
  dependsOn(testing.suites.named("functionalTest"))
}
