package com.nyasa.notifybridge.loc

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task that turns the bundled localization JSONs into typed Kotlin extensions.
 *
 * Inputs:
 * - [masterStringsFile] — canonical (English) source of truth.
 * - [validationStringsFiles] — all locale files (incl. master) used for parity checks.
 *
 * Outputs:
 * - [outputDirectory] — generated `{Scope}StringExtensions.kt` files.
 * - [reportDirectory] — `summary.md` + `summary.json`.
 *
 * The task fails the build if validation reports any ERROR-severity issues.
 */
@CacheableTask
abstract class LocalizationJsonToKotlinTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val masterStringsFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val validationStringsFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val reportDirectory: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val packageName: Property<String>

    @TaskAction
    fun generate() {
        val outDir = outputDirectory.asFile.get().apply {
            deleteRecursively()
            mkdirs()
        }
        val reportDir = reportDirectory.asFile.get().apply { mkdirs() }

        val masterFile = masterStringsFile.asFile.get()
        val master = DictionaryFileParser.parse(masterFile)

        val locales = LinkedHashMap<String, DictionaryModel>()
        for (file in validationStringsFiles.files) {
            if (!file.exists()) continue
            if (file.canonicalPath == masterFile.canonicalPath) continue
            val tag = file.parentFile.name
            locales[tag] = DictionaryFileParser.parse(file)
        }

        val errors = DictionaryValidator.validate(master, locales)
        val pkg = packageName.getOrElse("com.nyasa.notifybridge.localization")

        val fatal = errors.filter { it.severity == ValidationError.Severity.ERROR }
        // Always write Kotlin source so callers can inspect intermediate state, but…
        val kotlinResult = if (fatal.isEmpty()) {
            DictionaryKotlinWriter.write(master, outDir, pkg)
        } else {
            DictionaryKotlinWriter.Result(emptyList(), emptyList())
        }
        val masterTag = masterFile.parentFile.name
        val orderedLocales = (listOf(masterTag) + locales.keys).distinct()
        DictionaryReportWriter.write(master, orderedLocales, errors, kotlinResult, reportDir)

        if (fatal.isNotEmpty()) {
            val rendered = fatal.joinToString(separator = "\n") { "  - ${it.message}" }
            throw GradleException(
                "Typed-localization validation failed (${fatal.size} error(s)):\n$rendered\n" +
                    "See ${reportDir.resolve("summary.md").path}",
            )
        }
    }
}
