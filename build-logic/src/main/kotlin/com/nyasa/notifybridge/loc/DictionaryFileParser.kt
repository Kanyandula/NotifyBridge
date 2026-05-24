package com.nyasa.notifybridge.loc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Parses a localization JSON file into a [DictionaryModel].
 *
 * Expected shape:
 * ```
 * {
 *   "scope_name": { "key": "value with {placeholders}", ... },
 *   ...
 * }
 * ```
 *
 * Top-level keys are scopes; their values must be JSON objects of string entries.
 * String values are parsed by [PlaceholderParser] into [Token] lists.
 */
object DictionaryFileParser {

    private val json = Json { ignoreUnknownKeys = false; isLenient = false }

    fun parse(file: File): DictionaryModel {
        require(file.exists() && file.isFile) { "Missing localization file: $file" }
        val root = try {
            json.parseToJsonElement(file.readText()).jsonObject
        } catch (e: Exception) {
            throw DictionaryParseException("Failed to parse JSON: ${file.path}: ${e.message}", e)
        }
        val scopes = LinkedHashMap<String, DictionaryScope>()
        for ((scopeName, scopeElement) in root) {
            val scopeObject = scopeElement as? JsonObject
                ?: throw DictionaryParseException(
                    "Scope `$scopeName` in ${file.name} must be an object of string entries",
                )
            val entries = LinkedHashMap<String, Entry>()
            for ((key, valueElement) in scopeObject) {
                val primitive = valueElement as? JsonPrimitive
                if (primitive == null || !primitive.isString) {
                    throw DictionaryParseException(
                        "Entry `$scopeName.$key` in ${file.name} must be a string value",
                    )
                }
                val raw = primitive.jsonPrimitive.content
                val tokens = try {
                    PlaceholderParser.parse(raw)
                } catch (e: PlaceholderParseException) {
                    throw DictionaryParseException(
                        "Placeholder error in `$scopeName.$key` (${file.name}): ${e.message}",
                        e,
                    )
                }
                entries[key] = Entry(rawKey = key, rawValue = raw, tokens = tokens)
            }
            scopes[scopeName] = DictionaryScope(name = scopeName, entries = entries)
        }
        return DictionaryModel(scopes = scopes)
    }
}

class DictionaryParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
