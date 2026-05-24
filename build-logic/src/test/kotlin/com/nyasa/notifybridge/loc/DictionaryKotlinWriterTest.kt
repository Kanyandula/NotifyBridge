package com.nyasa.notifybridge.loc

import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.rules.TemporaryFolder
import java.io.File

class DictionaryKotlinWriterTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun emits_one_file_per_scope() {
        val model = build(
            "onboarding" to mapOf("title" to "Welcome"),
            "broker" to mapOf("save" to "Save"),
        )
        DictionaryKotlinWriter.write(model, tmp.root, "p")
        assertTrue(File(tmp.root, "p/OnboardingStringExtensions.kt").exists())
        assertTrue(File(tmp.root, "p/BrokerStringExtensions.kt").exists())
    }

    @Test fun no_arg_entry_emits_extension_val() {
        val model = build("onboarding" to mapOf("title" to "Welcome"))
        DictionaryKotlinWriter.write(model, tmp.root, "p")
        val text = File(tmp.root, "p/OnboardingStringExtensions.kt").readText()
        assertTrue(text.contains("public val Dictionary.Onboarding.title: LocalizedString"))
        assertTrue(text.contains("localizedString(\"title\")"))
    }

    @Test fun parameterized_entry_emits_extension_fun() {
        val model = build("status" to mapOf("hp" to "{host}:{port}"))
        DictionaryKotlinWriter.write(model, tmp.root, "p")
        val text = File(tmp.root, "p/StatusStringExtensions.kt").readText()
        assertTrue(text.contains("public fun Dictionary.Status.hp(host: Any, port: Any): LocalizedString"))
        assertTrue(text.contains("\"host\" to host"))
        assertTrue(text.contains("\"port\" to port"))
    }

    @Test fun plural_entry_uses_int_param_type() {
        val model = build("status" to mapOf("queued" to "{count, plural, one {# x} other {# xs}}"))
        DictionaryKotlinWriter.write(model, tmp.root, "p")
        val text = File(tmp.root, "p/StatusStringExtensions.kt").readText()
        assertTrue(text.contains("public fun Dictionary.Status.queued(count: Int): LocalizedString"))
    }

    @Test fun mixed_simple_plus_plural_lists_both_args_in_order() {
        val model = build(
            "apps" to mapOf("summary" to "{enabled} of {total, plural, one {# app} other {# apps}} forwarding"),
        )
        DictionaryKotlinWriter.write(model, tmp.root, "p")
        val text = File(tmp.root, "p/AppsStringExtensions.kt").readText()
        assertTrue(text.contains("public fun Dictionary.Apps.summary(enabled: Any, total: Int): LocalizedString"))
    }

    @Test fun kdoc_lists_key_and_placeholders() {
        val model = build("x" to mapOf("k" to "Hi {name}"))
        DictionaryKotlinWriter.write(model, tmp.root, "p")
        val text = File(tmp.root, "p/XStringExtensions.kt").readText()
        assertTrue(text.contains("* Key: k"))
        assertTrue(text.contains("* Placeholders: name"))
    }

    @Test fun kotlin_keyword_keys_are_suffixed() {
        val model = build("x" to mapOf("class" to "v"))
        val result = DictionaryKotlinWriter.write(model, tmp.root, "p")
        val text = File(tmp.root, "p/XStringExtensions.kt").readText()
        assertTrue(text.contains(".classString:"))
        assertTrue(result.keywordRewrites.any { it.contains("class") })
    }

    @Test fun colliding_names_resolved_with_ordinal_suffix() {
        // Two raw keys that camelCase to the same identifier.
        val model = build("x" to linkedMapOf("in-progress" to "a", "in_progress" to "b"))
        val result = DictionaryKotlinWriter.write(model, tmp.root, "p")
        val text = File(tmp.root, "p/XStringExtensions.kt").readText()
        assertTrue(text.contains(".inProgress:"))
        assertTrue(text.contains(".inProgress2:"))
        assertEquals(1, result.collisions.size)
    }

    private fun build(vararg scopes: Pair<String, Map<String, String>>): DictionaryModel {
        val scopeMap = scopes.associate { (name, entries) ->
            name to DictionaryScope(
                name,
                entries.mapValues { (k, v) -> Entry(k, v, PlaceholderParser.parse(v)) },
            )
        }
        return DictionaryModel(scopeMap)
    }
}
