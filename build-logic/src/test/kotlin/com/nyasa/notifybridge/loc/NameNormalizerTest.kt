package com.nyasa.notifybridge.loc

import org.junit.Test
import org.junit.Assert.assertEquals

class NameNormalizerTest {

    @Test fun snake_case_converts_to_camel_case() {
        assertEquals("ctaManageNotifications", NameNormalizer.toCamelCase("cta_manage_notifications"))
    }

    @Test fun kebab_case_converts_to_camel_case() {
        assertEquals("v1CaSxmPrice", NameNormalizer.toCamelCase("v1-ca-sxm-price"))
    }

    @Test fun dotted_case_converts_to_camel_case() {
        assertEquals("settingsHelpTitle", NameNormalizer.toCamelCase("settings.help.title"))
    }

    @Test fun pascal_case_first_letter_lowered() {
        assertEquals("title", NameNormalizer.toCamelCase("Title"))
    }

    @Test fun digit_leading_identifier_gets_underscore() {
        // "1st_place" → toCamelCase("1stPlace") → safe "_1stPlace"
        assertEquals("_1stPlace", NameNormalizer.safeKotlinIdentifier("1st_place"))
    }

    @Test fun kotlin_keyword_gets_string_suffix() {
        assertEquals("classString", NameNormalizer.safeKotlinIdentifier("class"))
        assertEquals("funString", NameNormalizer.safeKotlinIdentifier("fun"))
        assertEquals("objectString", NameNormalizer.safeKotlinIdentifier("object"))
    }

    @Test fun safe_identifier_passes_through_when_already_valid() {
        assertEquals("title", NameNormalizer.safeKotlinIdentifier("title"))
        assertEquals("userName", NameNormalizer.safeKotlinIdentifier("user_name"))
    }
}
