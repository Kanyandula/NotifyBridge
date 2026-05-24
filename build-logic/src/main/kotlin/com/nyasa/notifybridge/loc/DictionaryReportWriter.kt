package com.nyasa.notifybridge.loc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/**
 * Writes `summary.md` (human-readable) and `summary.json` (machine-readable) into the
 * report directory. Intended to make localization-sync PRs easier to review.
 */
object DictionaryReportWriter {

    fun write(
        model: DictionaryModel,
        locales: List<String>,
        errors: List<ValidationError>,
        kotlinResult: DictionaryKotlinWriter.Result,
        reportDir: File,
    ) {
        reportDir.mkdirs()

        val counts = scopeCounts(model)
        val totalEntries = counts.sumOf { it.entries }
        val totalParameterized = counts.sumOf { it.parameterized }
        val totalPlural = counts.sumOf { it.plural }

        writeMarkdown(reportDir, model, locales, counts, totalEntries, totalParameterized, totalPlural, errors, kotlinResult)
        writeJson(reportDir, locales, counts, totalEntries, totalParameterized, totalPlural, errors, kotlinResult)
    }

    private fun writeMarkdown(
        reportDir: File,
        model: DictionaryModel,
        locales: List<String>,
        counts: List<ScopeCount>,
        totalEntries: Int,
        totalParameterized: Int,
        totalPlural: Int,
        errors: List<ValidationError>,
        kotlinResult: DictionaryKotlinWriter.Result,
    ) {
        val sb = StringBuilder()
        sb.appendLine("# Typed localization summary")
        sb.appendLine()
        sb.appendLine("- Locales: ${locales.joinToString(", ")}")
        sb.appendLine("- Scopes: ${model.scopes.size}")
        sb.appendLine("- Total entries: $totalEntries")
        sb.appendLine("- Parameterized: $totalParameterized")
        sb.appendLine("- Plurals: $totalPlural")
        sb.appendLine()

        sb.appendLine("## Per-scope counts")
        sb.appendLine()
        sb.appendLine("| Scope | Entries | Parameterized | Plurals |")
        sb.appendLine("|-------|---------|---------------|---------|")
        for (c in counts) sb.appendLine("| ${c.name} | ${c.entries} | ${c.parameterized} | ${c.plural} |")
        sb.appendLine()

        if (kotlinResult.keywordRewrites.isNotEmpty()) {
            sb.appendLine("## Keyword rewrites")
            sb.appendLine()
            kotlinResult.keywordRewrites.forEach { sb.appendLine("- $it") }
            sb.appendLine()
        }
        if (kotlinResult.collisions.isNotEmpty()) {
            sb.appendLine("## Collisions resolved")
            sb.appendLine()
            kotlinResult.collisions.forEach { sb.appendLine("- $it") }
            sb.appendLine()
        }

        val fatal = errors.filter { it.severity == ValidationError.Severity.ERROR }
        val warnings = errors.filter { it.severity == ValidationError.Severity.WARNING }
        sb.appendLine("## Validation")
        sb.appendLine()
        sb.appendLine("- Errors: ${fatal.size}")
        sb.appendLine("- Warnings: ${warnings.size}")
        if (fatal.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("### Errors")
            sb.appendLine()
            fatal.forEach { sb.appendLine("- ${it.message}") }
        }
        if (warnings.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("### Warnings")
            sb.appendLine()
            warnings.forEach { sb.appendLine("- ${it.message}") }
        }
        File(reportDir, "summary.md").writeText(sb.toString())
    }

    private fun writeJson(
        reportDir: File,
        locales: List<String>,
        counts: List<ScopeCount>,
        totalEntries: Int,
        totalParameterized: Int,
        totalPlural: Int,
        errors: List<ValidationError>,
        kotlinResult: DictionaryKotlinWriter.Result,
    ) {
        fun s(v: String): JsonElement = JsonPrimitive(v)
        fun n(v: Int): JsonElement = JsonPrimitive(v)

        val root = JsonObject(
            mapOf(
                "locales" to JsonArray(locales.map(::s)),
                "totals" to JsonObject(
                    mapOf(
                        "scopes" to n(counts.size),
                        "entries" to n(totalEntries),
                        "parameterized" to n(totalParameterized),
                        "plurals" to n(totalPlural),
                    ),
                ),
                "scopes" to JsonArray(
                    counts.map { c ->
                        JsonObject(
                            mapOf(
                                "name" to s(c.name),
                                "entries" to n(c.entries),
                                "parameterized" to n(c.parameterized),
                                "plurals" to n(c.plural),
                            ),
                        )
                    },
                ),
                "keywordRewrites" to JsonArray(kotlinResult.keywordRewrites.map(::s)),
                "collisions" to JsonArray(kotlinResult.collisions.map(::s)),
                "errors" to JsonArray(
                    errors
                        .filter { it.severity == ValidationError.Severity.ERROR }
                        .map { s(it.message) },
                ),
                "warnings" to JsonArray(
                    errors
                        .filter { it.severity == ValidationError.Severity.WARNING }
                        .map { s(it.message) },
                ),
            ),
        )
        File(reportDir, "summary.json").writeText(Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), root))
    }

    private fun scopeCounts(model: DictionaryModel): List<ScopeCount> =
        model.scopes.values.map { scope ->
            val entries = scope.entries.size
            val parameterized = scope.entries.count { it.value.placeholders.isNotEmpty() }
            val plural = scope.entries.count { entry -> entry.value.placeholders.any { it.kind == Placeholder.Kind.PLURAL } }
            ScopeCount(scope.name, entries, parameterized, plural)
        }

    private data class ScopeCount(val name: String, val entries: Int, val parameterized: Int, val plural: Int)
}
