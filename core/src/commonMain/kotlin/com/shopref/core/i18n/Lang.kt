package com.shopref.core.i18n

/** Display language. English text doubles as the translation key for static strings. */
enum class Lang(val code: String, val nativeName: String) {
    EN("en", "English"),
    TR("tr", "Türkçe"),
    ;

    /** Picks between two inline translations (used for messages with embedded values). */
    fun t(en: String, tr: String): String = if (this == TR) tr else en

    /**
     * Translates a static string through [Dictionary], falling back to English. `{0}`, `{1}`…
     * placeholders are replaced by [args].
     */
    fun tx(en: String, vararg args: Any?): String {
        var s = if (this == TR) Dictionary.tr[en] ?: en else en
        args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
        return s
    }

    companion object {
        fun fromCode(code: String?): Lang = entries.firstOrNull { code != null && code.lowercase().startsWith(it.code) } ?: EN
    }
}
