package com.nyasa.notifybridge.loc

/**
 * Validates parity between the master (English) model and each locale model.
 *
 * Build-failing checks (severity = ERROR):
 * - Scope names match.
 * - Per-scope key sets match.
 * - Per-entry placeholder names + kinds match.
 * - Plural entries have the same set of arms (e.g. `one`, `other`) across locales.
 */
object DictionaryValidator {

    fun validate(
        master: DictionaryModel,
        locales: Map<String, DictionaryModel>,
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val masterScopes = master.scopes.keys

        for ((tag, model) in locales) {
            val theirScopes = model.scopes.keys
            (masterScopes - theirScopes).forEach { missing ->
                errors += ValidationError(
                    ValidationError.Severity.ERROR,
                    "Locale `$tag` is missing scope `$missing`",
                )
            }
            (theirScopes - masterScopes).forEach { extra ->
                errors += ValidationError(
                    ValidationError.Severity.ERROR,
                    "Locale `$tag` has unknown scope `$extra` not present in master",
                )
            }

            for (scopeName in masterScopes intersect theirScopes) {
                val masterScope = master.scopes.getValue(scopeName)
                val theirScope = model.scopes.getValue(scopeName)
                val masterKeys = masterScope.entries.keys
                val theirKeys = theirScope.entries.keys
                (masterKeys - theirKeys).forEach { missing ->
                    errors += ValidationError(
                        ValidationError.Severity.ERROR,
                        "Locale `$tag` is missing key `$scopeName.$missing`",
                    )
                }
                (theirKeys - masterKeys).forEach { extra ->
                    errors += ValidationError(
                        ValidationError.Severity.ERROR,
                        "Locale `$tag` has unknown key `$scopeName.$extra` not present in master",
                    )
                }

                for (key in masterKeys intersect theirKeys) {
                    val m = masterScope.entries.getValue(key)
                    val t = theirScope.entries.getValue(key)
                    errors += comparePlaceholders(tag, scopeName, key, m, t)
                    errors += comparePluralArms(tag, scopeName, key, m, t)
                }
            }
        }
        return errors
    }

    private fun comparePlaceholders(tag: String, scope: String, key: String, master: Entry, other: Entry): List<ValidationError> {
        val out = mutableListOf<ValidationError>()
        val mIndex = master.placeholders.associateBy { it.name }
        val oIndex = other.placeholders.associateBy { it.name }

        (mIndex.keys - oIndex.keys).forEach { missing ->
            out += ValidationError(
                ValidationError.Severity.ERROR,
                "`$scope.$key` in locale `$tag` is missing placeholder `{$missing}`",
            )
        }
        (oIndex.keys - mIndex.keys).forEach { extra ->
            out += ValidationError(
                ValidationError.Severity.ERROR,
                "`$scope.$key` in locale `$tag` has unknown placeholder `{$extra}`",
            )
        }
        for (name in mIndex.keys intersect oIndex.keys) {
            val mKind = mIndex.getValue(name).kind
            val oKind = oIndex.getValue(name).kind
            if (mKind != oKind) {
                out += ValidationError(
                    ValidationError.Severity.ERROR,
                    "`$scope.$key` placeholder `{$name}` is $mKind in master but $oKind in locale `$tag`",
                )
            }
        }
        return out
    }

    private fun comparePluralArms(tag: String, scope: String, key: String, master: Entry, other: Entry): List<ValidationError> {
        val out = mutableListOf<ValidationError>()
        val mPlurals = collectPlurals(master.tokens).associateBy { it.argName }
        val oPlurals = collectPlurals(other.tokens).associateBy { it.argName }
        for (name in mPlurals.keys intersect oPlurals.keys) {
            val mArms = mPlurals.getValue(name).branches.keys
            val oArms = oPlurals.getValue(name).branches.keys
            if (mArms != oArms) {
                out += ValidationError(
                    ValidationError.Severity.ERROR,
                    "`$scope.$key` plural `{$name}` has arms $mArms in master but $oArms in locale `$tag`",
                )
            }
        }
        return out
    }

    private fun collectPlurals(tokens: List<Token>): List<Token.Plural> {
        val out = mutableListOf<Token.Plural>()
        fun walk(list: List<Token>) {
            for (t in list) when (t) {
                is Token.Plural -> {
                    out += t
                    t.branches.values.forEach(::walk)
                }
                is Token.Literal, is Token.Simple, Token.PluralCount -> Unit
            }
        }
        walk(tokens)
        return out
    }
}

data class ValidationError(val severity: Severity, val message: String) {
    enum class Severity { ERROR, WARNING }
}
