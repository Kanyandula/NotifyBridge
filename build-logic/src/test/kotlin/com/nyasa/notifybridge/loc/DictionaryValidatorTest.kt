package com.nyasa.notifybridge.loc

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

class DictionaryValidatorTest {

    @Test fun matching_dictionaries_produce_no_errors() {
        val master = build("hi" to "Hello", "bye" to "Goodbye")
        val fr = build("hi" to "Bonjour", "bye" to "Au revoir")
        val errors = DictionaryValidator.validate(master, mapOf("fr" to fr))
        assertTrue(errors.isEmpty())
    }

    @Test fun missing_key_in_locale_is_reported() {
        val master = build("hi" to "Hello", "bye" to "Goodbye")
        val fr = build("hi" to "Bonjour")
        val errors = DictionaryValidator.validate(master, mapOf("fr" to fr))
        assertTrue(errors.any { it.message.contains("missing key") && it.message.contains("bye") })
    }

    @Test fun extra_key_in_locale_is_reported() {
        val master = build("hi" to "Hello")
        val fr = build("hi" to "Bonjour", "extra" to "Bonus")
        val errors = DictionaryValidator.validate(master, mapOf("fr" to fr))
        assertTrue(errors.any { it.message.contains("unknown key") && it.message.contains("extra") })
    }

    @Test fun mismatched_placeholder_set_is_reported() {
        val master = build("greet" to "Hi {name}")
        val fr = build("greet" to "Bonjour {nom}")
        val errors = DictionaryValidator.validate(master, mapOf("fr" to fr))
        assertTrue(errors.any { it.message.contains("missing placeholder") })
        assertTrue(errors.any { it.message.contains("unknown placeholder") })
    }

    @Test fun plural_arm_mismatch_is_reported() {
        val master = build("queued" to "{count, plural, one {# item} other {# items}}")
        // 'other' arm omitted — parser would reject this at parse time, so we
        // craft a case where 'few' arm is added to locale (unsupported but
        // useful to check arm-set diffing if a parser extension ever allows it).
        val fr = DictionaryModel(
            scopes = mapOf(
                "x" to DictionaryScope(
                    "x",
                    mapOf(
                        "queued" to Entry(
                            "queued",
                            "{count, plural, one {} other {} few {}}",
                            // Hand-build tokens to bypass parser strictness:
                            listOf(
                                Token.Plural(
                                    "count",
                                    mapOf("one" to emptyList(), "other" to emptyList(), "few" to emptyList()),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val errors = DictionaryValidator.validate(master, mapOf("fr" to fr))
        assertTrue(errors.any { it.message.contains("arms") })
    }

    @Test fun extra_scope_in_locale_is_reported() {
        val master = build("hi" to "Hello")
        val fr = DictionaryModel(
            scopes = mapOf(
                "x" to master.scopes.getValue("x"),
                "bonus" to DictionaryScope("bonus", mapOf("k" to Entry("k", "v", listOf(Token.Literal("v"))))),
            ),
        )
        val errors = DictionaryValidator.validate(master, mapOf("fr" to fr))
        assertTrue(errors.any { it.message.contains("unknown scope") && it.message.contains("bonus") })
    }

    @Test fun severity_separation_for_clean_and_dirty_models() {
        val master = build("hi" to "Hi {name}")
        val fr = build("hi" to "Bonjour {nom}")
        val errors = DictionaryValidator.validate(master, mapOf("fr" to fr))
        val fatal = errors.count { it.severity == ValidationError.Severity.ERROR }
        val warn = errors.count { it.severity == ValidationError.Severity.WARNING }
        assertEquals(2, fatal)
        assertEquals(0, warn)
    }

    private fun build(vararg pairs: Pair<String, String>): DictionaryModel {
        val entries = pairs.associate { (k, v) -> k to Entry(k, v, PlaceholderParser.parse(v)) }
        return DictionaryModel(mapOf("x" to DictionaryScope("x", entries)))
    }
}
