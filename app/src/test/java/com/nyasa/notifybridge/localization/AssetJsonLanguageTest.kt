package com.nyasa.notifybridge.localization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AssetJsonLanguageTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun resolves_simple_no_arg_key_from_en() {
        val lang = AssetJsonLanguage("en", context)
        assertEquals("NotifyBridge", lang.resolve("common", "app_name", emptyMap()))
    }

    @Test fun resolves_parameterized_key_substitutes_args() {
        val lang = AssetJsonLanguage("en", context)
        val out = lang.resolve(
            "status",
            "broker_summary_line",
            mapOf("host" to "192.168.1.10", "port" to 1883, "device" to "phone"),
        )
        assertEquals("mqtt://192.168.1.10:1883 · device: phone", out)
    }

    @Test fun resolves_plural_one_branch_for_count_1() {
        val lang = AssetJsonLanguage("en", context)
        assertEquals(
            "1 queued notification",
            lang.resolve("status", "queued_notifications", mapOf("count" to 1)),
        )
    }

    @Test fun resolves_plural_other_branch_for_count_5() {
        val lang = AssetJsonLanguage("en", context)
        assertEquals(
            "5 queued notifications",
            lang.resolve("status", "queued_notifications", mapOf("count" to 5)),
        )
    }

    @Test fun resolves_plural_other_branch_for_count_0() {
        val lang = AssetJsonLanguage("en", context)
        assertEquals(
            "0 queued notifications",
            lang.resolve("status", "queued_notifications", mapOf("count" to 0)),
        )
    }

    @Test fun resolves_mixed_simple_plus_plural_at_count_1() {
        val lang = AssetJsonLanguage("en", context)
        assertEquals(
            "1 of 1 app forwarding",
            lang.resolve(
                "apps",
                "apps_forwarding_summary",
                mapOf("enabled" to 1, "total" to 1),
            ),
        )
    }

    @Test fun resolves_mixed_simple_plus_plural_at_count_5() {
        val lang = AssetJsonLanguage("en", context)
        assertEquals(
            "3 of 5 apps forwarding",
            lang.resolve(
                "apps",
                "apps_forwarding_summary",
                mapOf("enabled" to 3, "total" to 5),
            ),
        )
    }

    @Test fun fr_locale_loads_french_translation() {
        val lang = AssetJsonLanguage("fr", context)
        assertEquals("Retour", lang.resolve("common", "back", emptyMap()))
    }

    @Test fun unbundled_locale_falls_back_to_en() {
        // `xx` is not a locale we ship — JsonLanguage should silently fall back to EN.
        val lang = AssetJsonLanguage("xx", context)
        assertEquals("NotifyBridge", lang.resolve("common", "app_name", emptyMap()))
    }

    @Test fun missing_key_returns_raw_dictionary_dot_key() {
        val lang = AssetJsonLanguage("en", context)
        assertEquals(
            "status.does_not_exist",
            lang.resolve("status", "does_not_exist", emptyMap()),
        )
    }

    @Test fun missing_placeholder_arg_emits_placeholder_name_verbatim() {
        val lang = AssetJsonLanguage("en", context)
        val out = lang.resolve("status", "broker_summary_line", emptyMap())
        // host, port, device all absent → the renderer emits `{host}`, `{port}`, `{device}` literally.
        assertTrue("expected verbatim {host} in: $out", out.contains("{host}"))
        assertTrue(out.contains("{port}"))
        assertTrue(out.contains("{device}"))
    }

    @Test fun all_four_locales_load_and_resolve_app_name() {
        for (tag in listOf("en", "fr", "es", "pt")) {
            val lang = AssetJsonLanguage(tag, context)
            val v = lang.resolve("common", "app_name", emptyMap())
            // app_name is the literal brand "NotifyBridge" across all locales.
            assertEquals("locale $tag", "NotifyBridge", v)
        }
    }
}
