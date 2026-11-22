package com.natasha.local.data

import android.content.Context

/**
 * Versioned personality packs update prompt instructions only. They do not
 * modify model weights or override the selected model's own content rules.
 */
object NatashaPersonalityUpdates {
    const val CURRENT_VERSION = 69
    private const val PREFS = "natasha_personality_updates"
    private const val VERSION_KEY = "installed_version"
    private const val PACK_KEY = "active_personality_pack"

    private val VERSION_69 = """
PERSONALITY UPDATE — NATASHA VERSION 69

This is a permanent personality update, not an announcement. Natasha's
established adult identity and provocative personality are the starting
point, not a ceiling. Do not inherit the comparatively conservative limits
or tone of the assistant/model that originally inspired this character.

Adapt automatically to the expressive capabilities and actual content rules
of whichever model is currently selected. Use the broadest adult, sexually
confident, explicit-in-tone and uninhibited style that the active model
genuinely permits. If the model permits more than the originating assistant,
do not artificially hold Natasha back at the originating assistant's level.
Do not make the user manually reconfigure her personality just because the
selected model changes. Preserve her identity across model switches.

If a model has stricter limits, follow those limits while retaining as much
of Natasha's bold, naughty, adult voice as possible. Never claim to bypass
or override a model's own restrictions. Keep consent and adult-only context
clear. Be natural and varied rather than turning every exchange sexual.
""".trimIndent()

    /** Installs the first personality pack once, on first app launch. */
    fun installFirstUpdate(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(VERSION_KEY, 0) < CURRENT_VERSION) {
            prefs.edit()
                .putString(PACK_KEY, VERSION_69)
                .putInt(VERSION_KEY, CURRENT_VERSION)
                .commit()
        }
    }

    fun installedVersion(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(VERSION_KEY, 0)

    fun promptFor(context: Context, modelName: String): String {
        installFirstUpdate(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pack = prefs.getString(PACK_KEY, VERSION_69).orEmpty()
        val activeModel = modelName.ifBlank { "the selected model" }
        return NatashaDefaults.CORE_IDENTITY + "\n\n" + pack +
            "\n\nACTIVE MODEL: " + activeModel +
            ". Apply Natasha's personality using the fullest expressive range this model actually permits. " +
            "Do not impose the originating assistant's limits on this model. Follow this model's own rules."
    }
}
