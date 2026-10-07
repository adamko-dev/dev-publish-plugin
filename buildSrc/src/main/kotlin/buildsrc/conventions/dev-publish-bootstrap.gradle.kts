@file:Suppress("UnstableApiUsage")

package buildsrc.conventions

import dev.adamko.gradle.kotlin.dsl.resolvable
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Category.LIBRARY
import org.gradle.api.attributes.LibraryElements.JAR
import org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE
import org.gradle.api.attributes.Usage.JAVA_RUNTIME
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE

abstract class DevPublishBootstrapExtension
internal constructor() {

  /**
   * Resolves the DevPublish jars this build uses to build itself.
   *
   * Returned as files, not dependencies, so they don't conflict with the subprojects that have the
   * same coordinates.
   */
  val bootstrapDevPublishJars: FileCollection
    get() = _bootstrapDevPublishJars

  abstract val modules: SetProperty<String>

  internal abstract val _bootstrapDevPublishJars: ConfigurableFileCollection
}

val bootstrapExt =
  extensions.create<DevPublishBootstrapExtension>("devPublishBootstrap").apply {
    modules.convention(
      setOf(
        "dev-publish-common",
        "dev-publish-plugin",
        "dev-publish-utils",
      )
    )
  }

val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

val devPublishBootstrap by project.configurations.dependencyScope {
  description = "DevPublish jars this build uses to build itself."
  defaultDependencies {
    addAllLater(
      bootstrapExt.modules.map { modules ->
        modules.map { module ->
          val alias = "devPublishBootstrap-" + module.removePrefix("dev-publish-")
          libs.findLibrary(alias)
            .orElseThrow { error("missing version catalog library $alias") }
            .get()
        }
      }
    )
  }
}

val devPublishBootstrapResolver by project.configurations.resolvable {
  extendsFrom(devPublishBootstrap)
  isTransitive = false
  attributes {
    attribute(USAGE_ATTRIBUTE, objects.named(JAVA_RUNTIME))
    attribute(CATEGORY_ATTRIBUTE, objects.named(LIBRARY))
    attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(JAR))
  }
}

bootstrapExt._bootstrapDevPublishJars.from(
  providers.zip(devPublishBootstrapResolver, bootstrapExt.modules) { c, modules ->
    c.incoming
      .artifactView {
        componentFilter { id ->
          id is ModuleComponentIdentifier
              && id.module in modules
        }
      }
      .files
  }
)
