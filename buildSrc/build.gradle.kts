import org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion

plugins {
  `kotlin-dsl`
}

dependencies {
  implementation("org.gradle.kotlin:gradle-kotlin-dsl-plugins:$expectedKotlinDslPluginsVersion")
  implementation(embeddedKotlin("gradle-plugin"))

  implementation(libs.gradlePlugin.pluginPublishPlugin)
  implementation(libs.gradlePlugin.nmcp)
  implementation(gradleKotlinAccessorsLibs.accessors)

  implementation(libs.devPublishBootstrap.plugin)
  implementation(libs.devPublishBootstrap.common)
}
