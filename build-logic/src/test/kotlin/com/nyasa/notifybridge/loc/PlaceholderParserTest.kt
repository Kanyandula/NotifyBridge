package com.nyasa.notifybridge.loc

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue

class PlaceholderParserTest {

    @Test fun plain_text_yields_single_literal() {
        val tokens = PlaceholderParser.parse("Hello world")
        assertEquals(listOf(Token.Literal("Hello world")), tokens)
    }

    @Test fun simple_placeholder_is_extracted() {
        val tokens = PlaceholderParser.parse("Hi {name}!")
        assertEquals(
            listOf(Token.Literal("Hi "), Token.Simple("name"), Token.Literal("!")),
            tokens,
        )
    }

    @Test fun multiple_placeholders_in_sequence() {
        val tokens = PlaceholderParser.parse("mqtt://{host}:{port}")
        assertEquals(
            listOf(
                Token.Literal("mqtt://"),
                Token.Simple("host"),
                Token.Literal(":"),
                Token.Simple("port"),
            ),
            tokens,
        )
    }

    @Test fun snake_case_placeholder_preserved_in_token() {
        // Normalization to Kotlin identifiers happens in NameNormalizer, not the parser.
        val tokens = PlaceholderParser.parse("Hello {user_name}")
        assertEquals(
            listOf(Token.Literal("Hello "), Token.Simple("user_name")),
            tokens,
        )
    }

    @Test fun plural_with_one_and_other_arms() {
        val tokens = PlaceholderParser.parse("{count, plural, one {# item} other {# items}}")
        assertEquals(1, tokens.size)
        val plural = tokens.single() as Token.Plural
        assertEquals("count", plural.argName)
        assertEquals(setOf("one", "other"), plural.branches.keys)
        assertEquals(
            listOf(Token.PluralCount, Token.Literal(" item")),
            plural.branches["one"],
        )
        assertEquals(
            listOf(Token.PluralCount, Token.Literal(" items")),
            plural.branches["other"],
        )
    }

    @Test fun mixed_simple_and_plural_in_same_string() {
        val tokens = PlaceholderParser.parse(
            "{enabled} of {total, plural, one {# app} other {# apps}} forwarding",
        )
        // Expect: Simple("enabled"), Literal, Plural("total", ...), Literal
        assertEquals(4, tokens.size)
        assertTrue(tokens[0] is Token.Simple)
        assertTrue(tokens[2] is Token.Plural)
        val plural = tokens[2] as Token.Plural
        assertEquals("total", plural.argName)
        assertEquals(setOf("one", "other"), plural.branches.keys)
    }

    @Test fun unmatched_brace_throws() {
        assertThrows(PlaceholderParseException::class.java) {
            PlaceholderParser.parse("Hello {name")
        }
    }

    @Test fun unsupported_format_kind_rejected() {
        // `select` is intentionally not in our v1 subset.
        assertThrows(IllegalArgumentException::class.java) {
            PlaceholderParser.parse("{gender, select, male {him} female {her} other {them}}")
        }
    }

    @Test fun plural_missing_other_branch_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PlaceholderParser.parse("{count, plural, one {# item}}")
        }
    }

    @Test fun hash_outside_plural_kept_as_pluralcount_token() {
        // Documenting current behavior: `#` is always tokenized as PluralCount.
        // The writer/parser only references it from inside a plural branch; at
        // top level it's effectively meaningless but parses cleanly.
        val tokens = PlaceholderParser.parse("rank #1")
        assertEquals(
            listOf(Token.Literal("rank "), Token.PluralCount, Token.Literal("1")),
            tokens,
        )
    }
}
