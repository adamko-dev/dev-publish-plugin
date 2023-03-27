package buildsrc.utils

import org.gradle.api.Project
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.kotlin.dsl.get

/**
 * Don't publish test fixtures (which causes warnings when publishing)
 *
 * https://docs.gradle.org/current/userguide/java_testing.html#publishing_test_fixtures
 */
fun Project.skipTestFixturesPublications() {
  val javaComponent = components["java"] as AdhocComponentWithVariants
  javaComponent.withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
  javaComponent.withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
}
