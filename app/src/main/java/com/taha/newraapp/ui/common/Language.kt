package com.taha.newraapp.ui.common

/**
 * Supported languages for the app.
 */
enum class Language(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    FRENCH("fr", "Français"),
    ARABIC("ar", "العربية");

    companion object {
        fun fromCode(code: String): Language {
            return entries.find { it.code == code } ?: ENGLISH
        }
    }
}
