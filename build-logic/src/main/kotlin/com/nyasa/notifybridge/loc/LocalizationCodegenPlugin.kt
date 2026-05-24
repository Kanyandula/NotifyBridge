package com.nyasa.notifybridge.loc

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/**
 * Applies typed-localization codegen to an Android project.
 *
 * For each variant, registers a `generate<Variant>TypedLocalization` task that reads
 * `src/main/assets/localization/<locale>/strings.json` and emits Kotlin extensions
 * into the variant's generated-source set.
 *
 * Apply via plugin id: `nyasa.localization-codegen`. The plugin defers its real
 * configuration until the host Android plugin has applied, so plugin order in the
 * consumer's `plugins { ... }` block does not matter.
 */
class LocalizationCodegenPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        var configured = false
        val configure: (Any) -> Unit = {
            if (!configured) {
                configured = true
                configureAndroid(project)
            }
        }
        project.plugins.withId("com.android.application", configure)
        project.plugins.withId("com.android.library", configure)
        project.afterEvaluate {
            check(configured) {
                "nyasa.localization-codegen requires the com.android.application or " +
                    "com.android.library plugin to be applied to the same project."
            }
        }
    }

    private fun configureAndroid(project: Project) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)

        val assetsDir = project.layout.projectDirectory.dir("src/main/assets/localization")
        val masterFile = assetsDir.file("en/strings.json")

        // Discover all locale JSONs at configuration time so they participate in
        // task input snapshotting (build cache + up-to-date checks).
        val allLocaleFiles = assetsDir.asFile.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val f = java.io.File(dir, "strings.json")
                if (f.exists()) f else null
            }
            .orEmpty()

        androidComponents.onVariants { variant ->
            val capitalized = variant.name.replaceFirstChar { it.uppercase() }
            val task = project.tasks.register<LocalizationJsonToKotlinTask>(
                "generate${capitalized}TypedLocalization",
            ) {
                group = "build"
                description = "Generates typed localization API from JSON for the ${variant.name} variant."
                masterStringsFile.set(masterFile)
                validationStringsFiles.from(allLocaleFiles)
                outputDirectory.set(
                    project.layout.buildDirectory.dir("generated/source/localization/${variant.name}"),
                )
                reportDirectory.set(
                    project.layout.buildDirectory.dir("reports/typed-localization/${variant.name}"),
                )
                packageName.set("com.nyasa.notifybridge.localization")
            }

            variant.sources.java?.addGeneratedSourceDirectory(task) { it.outputDirectory }
        }
    }
}
