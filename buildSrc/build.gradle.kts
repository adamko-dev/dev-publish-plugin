import org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion

plugins {
  `kotlin-dsl`
}

dependencies {
  implementation(fileTree("libs") { include("*.jar") })

  implementation("org.gradle.kotlin:gradle-kotlin-dsl-plugins:$expectedKotlinDslPluginsVersion")
  // for the `kotlin-jvm-library` convention, which is not a Gradle plugin module
  implementation(embeddedKotlin("gradle-plugin"))

  implementation(libs.gradlePlugin.pluginPublishPlugin)
  implementation(libs.gradlePlugin.nmcp)
}
