package com.nyasa.notifybridge.loc

import java.io.File

/**
 * Emits one Kotlin file per scope, containing extension `val`s (no-arg entries)
 * and extension `fun`s (parameterized entries) on `Dictionary.<Scope>`.
 *
 * Output path: `<outputDir>/<packagePath>/<Scope>StringExtensions.kt`.
 *
 * Within a scope, member-name collisions are resolved deterministically by
 * appending an ordinal suffix (`title`, `title2`, …) in raw-JSON key order.
 */
object DictionaryKotlinWriter {

    data class Result(
        val collisions: List<String>,
        val keywordRewrites: List<String>,
    )

    fun write(
        model: DictionaryModel,
        outputDir: File,
        packageName: String,
    ): Result {
        val packageDir = File(outputDir, packageName.replace('.', '/')).also { it.mkdirs() }
        val collisions = mutableListOf<String>()
        val keywordRewrites = mutableListOf<String>()

        for (scope in model.scopes.values) {
            val className = scope.name.replaceFirstChar { it.uppercase() }
            val file = File(packageDir, "${className}StringExtensions.kt")
            val text = buildScopeFile(packageName, className, scope, collisions, keywordRewrites)
            file.writeText(text)
        }

        return Result(collisions, keywordRewrites)
    }

    private fun buildScopeFile(
        packageName: String,
        className: String,
        scope: DictionaryScope,
        collisions: MutableList<String>,
        keywordRewrites: MutableList<String>,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("// AUTO-GENERATED — DO NOT EDIT BY HAND.")
        sb.appendLine("// Edit app/src/main/assets/localization/<locale>/strings.json instead,")
        sb.appendLine("// then re-run `:app:generate<Variant>TypedLocalization`.")
        sb.appendLine("@file:Suppress(\"MaxLineLength\", \"unused\", \"RedundantVisibilityModifier\")")
        sb.appendLine()
        sb.appendLine("package $packageName")
        sb.appendLine()

        val usedNames = mutableSetOf<String>()

        for ((rawKey, entry) in scope.entries) {
            val baseName = NameNormalizer.safeKotlinIdentifier(rawKey)
            val camel = NameNormalizer.toCamelCase(rawKey)
            if (baseName != camel) keywordRewrites += "${scope.name}.$rawKey → $baseName"

            val finalName = uniqueName(baseName, usedNames).also { name ->
                if (name != baseName) collisions += "${scope.name}.$rawKey → $name"
            }
            usedNames += finalName

            sb.append(renderEntry(className, entry, finalName))
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun uniqueName(base: String, used: Set<String>): String {
        if (base !in used) return base
        var n = 2
        while ("$base$n" in used) n++
        return "$base$n"
    }

    private fun renderEntry(className: String, entry: Entry, memberName: String): String {
        val placeholders = entry.placeholders
        val kdoc = buildKDoc(entry.rawKey, placeholders)
        return if (placeholders.isEmpty()) {
            buildString {
                append(kdoc)
                append("public val Dictionary.$className.$memberName: LocalizedString\n")
                append("    get() = localizedString(${entry.rawKey.kotlinLiteral()})\n")
            }
        } else {
            val params = placeholders.joinToString(", ") { p ->
                val type = if (p.kind == Placeholder.Kind.PLURAL) "Int" else "Any"
                "${NameNormalizer.safeKotlinIdentifier(p.name)}: $type"
            }
            val pairs = placeholders.joinToString(", ") { p ->
                "${p.name.kotlinLiteral()} to ${NameNormalizer.safeKotlinIdentifier(p.name)}"
            }
            buildString {
                append(kdoc)
                append("public fun Dictionary.$className.$memberName($params): LocalizedString =\n")
                append("    localizedString(${entry.rawKey.kotlinLiteral()}, $pairs)\n")
            }
        }
    }

    private fun buildKDoc(rawKey: String, placeholders: List<Placeholder>): String {
        val sb = StringBuilder()
        sb.append("/**\n")
        sb.append(" * Key: $rawKey\n")
        if (placeholders.isNotEmpty()) {
            val rendered = placeholders.joinToString(", ") { p ->
                if (p.kind == Placeholder.Kind.PLURAL) "${p.name} (plural)" else p.name
            }
            sb.append(" * Placeholders: $rendered\n")
        }
        sb.append(" */\n")
        return sb.toString()
    }

    private fun String.kotlinLiteral(): String {
        val escaped = this
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        return "\"$escaped\""
    }
}
