/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.keyboard

import kotlinx.serialization.json.Json
import org.fcitx.fcitx5.android.core.data.DataManager
import org.fcitx.fcitx5.android.data.keyboard.KeyboardLayoutFile.Key
import org.fcitx.fcitx5.android.data.keyboard.KeyboardLayoutFile.Variant
import org.fcitx.fcitx5.android.input.keyboard.AlphabetKey
import org.fcitx.fcitx5.android.input.keyboard.BackspaceKey
import org.fcitx.fcitx5.android.input.keyboard.CapsKey
import org.fcitx.fcitx5.android.input.keyboard.CommaKey
import org.fcitx.fcitx5.android.input.keyboard.KeyDef
import org.fcitx.fcitx5.android.input.keyboard.LanguageKey
import org.fcitx.fcitx5.android.input.keyboard.LayoutSwitchKey
import org.fcitx.fcitx5.android.input.keyboard.QuickPhraseKey
import org.fcitx.fcitx5.android.input.keyboard.ReturnKey
import org.fcitx.fcitx5.android.input.keyboard.SpaceKey
import org.fcitx.fcitx5.android.input.keyboard.SymbolKey
import org.fcitx.fcitx5.android.utils.appContext
import timber.log.Timber
import java.io.File

/**
 * Loads text keyboard layouts from `<name>.json`, user files take precedence over the ones
 * shipped by plugins. Should only be accessed from the main thread.
 */
object KeyboardLayoutManager {

    const val DEFAULT = "default"

    private const val EXT = "json"

    private val builtinDir = File(DataManager.dataDir, "usr/share/fcitx5/keyboard-layout")

    private val customDir = File(
        appContext.getExternalFilesDir(null)!!, "data/keyboard-layout"
    ).also { it.mkdirs() }

    private class Cached(val file: File, val lastModified: Long, val layout: List<List<KeyDef>>?)

    private val cache = hashMapOf<String, Cached>()

    /**
     * @return `null` if there is no such layout or it fails to load.
     * The same instance is returned until the file changes.
     */
    fun load(name: String): List<List<KeyDef>>? {
        val file = sequenceOf(customDir, builtinDir)
            .map { File(it, "$name.$EXT") }
            .firstOrNull { it.isFile }
        if (file == null) {
            cache.remove(name)
            return null
        }
        val lastModified = file.lastModified()
        cache[name]?.let {
            if (it.file == file && it.lastModified == lastModified) return it.layout
        }
        val layout = runCatching {
            Json.decodeFromString<KeyboardLayoutFile>(file.readText())
                .also { it.validate() }
                .rows.map { row -> row.map { it.toKeyDef() } }
        }.getOrElse {
            Timber.w("Failed to load keyboard layout ${file.absolutePath}: ${it.message}")
            null
        }
        cache[name] = Cached(file, lastModified, layout)
        return layout
    }

    private fun Variant.toKeyDef() = when (this) {
        Variant.Normal -> KeyDef.Appearance.Variant.Normal
        Variant.AltForeground -> KeyDef.Appearance.Variant.AltForeground
        Variant.Alternative -> KeyDef.Appearance.Variant.Alternative
        Variant.Accent -> KeyDef.Appearance.Variant.Accent
    }

    private fun Key.toKeyDef(): KeyDef = when (this) {
        is Key.Alphabet -> AlphabetKey(main, alt, variant.toKeyDef(), percentWidth = width)
        is Key.Symbol -> SymbolKey(text, width, variant.toKeyDef())
        is Key.LayoutSwitch -> LayoutSwitchKey(label, to, width)
        is Key.Comma -> CommaKey(width, variant.toKeyDef())
        is Key.Caps -> CapsKey(width)
        is Key.Backspace -> BackspaceKey(width)
        is Key.QuickPhrase -> QuickPhraseKey(width)
        is Key.Language -> LanguageKey(width)
        is Key.Space -> SpaceKey()
        is Key.Return -> ReturnKey(width)
    }
}
