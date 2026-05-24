package com.nyasa.notifybridge.loc

import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * End-to-end test against the real bundled JSON files. Skipped if the app source
 * tree isn't reachable from the test working directory.
 */
class IntegrationTest {

    @get:Rule val tmp = TemporaryFolder()

    private val assetsRoot = File("../app/src/main/assets/localization").canonicalFile

    @Test fun real_master_and_locales_parse_and_validate_clean() {
        assumeTrue("assets root not reachable: $assetsRoot", assetsRoot.isDirectory)

        val master = DictionaryFileParser.parse(File(assetsRoot, "en/strings.json"))
        val others = listOf("fr", "es", "pt")
            .associateWith { DictionaryFileParser.parse(File(assetsRoot, "$it/strings.json")) }

        val errors = DictionaryValidator.validate(master, others)
        assertTrue(
            "Validation errors against bundled JSON: ${errors.joinToString("\n") { it.message }}",
            errors.none { it.severity == ValidationError.Severity.ERROR },
        )
    }

    @Test fun real_master_generates_expected_scope_files() {
        assumeTrue("assets root not reachable: $assetsRoot", assetsRoot.isDirectory)
        val master = DictionaryFileParser.parse(File(assetsRoot, "en/strings.json"))
        DictionaryKotlinWriter.write(master, tmp.root, "com.nyasa.notifybridge.localization")

        val packageDir = File(tmp.root, "com/nyasa/notifybridge/localization")
        val expected = setOf(
            "CommonStringExtensions.kt",
            "OnboardingStringExtensions.kt",
            "StatusStringExtensions.kt",
            "BrokerStringExtensions.kt",
            "AppsStringExtensions.kt",
            "PermissionsStringExtensions.kt",
            "LanguageStringExtensions.kt",
            "LockedStringExtensions.kt",
            "ServicesStringExtensions.kt",
        )
        val actual = packageDir.list()?.toSet().orEmpty()
        assertEquals(expected, actual)
    }
}
