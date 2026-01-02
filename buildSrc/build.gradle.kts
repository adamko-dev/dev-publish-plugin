import org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion

plugins {
  `kotlin-dsl`
}

dependencies {
  implementation("org.gradle.kotlin:gradle-kotlin-dsl-plugins:$expectedKotlinDslPluginsVersion")

  implementation(libs.gradlePlugin.pluginPublishPlugin)
  //implementation(libs.gradlePlugin.bcvMu)
}
