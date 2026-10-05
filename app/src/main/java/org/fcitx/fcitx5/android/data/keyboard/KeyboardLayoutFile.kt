/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.keyboard

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Text keyboard layout loaded from a json file.
 *
 * Every [Key] maps to exactly one key preset, so a layout can only rearrange existing keys.
 */
@Serializable
data class KeyboardLayoutFile(
    val version: Int,
    val rows: List<List<Key>>
) {
    @Serializable
    enum class Variant {
        @SerialName("normal")
        Normal,

        @SerialName("alt-foreground")
        AltForeground,

        @SerialName("alternative")
        Alternative,

        @SerialName("accent")
        Accent
    }

    @Serializable
    sealed class Key {
        /**
         * Keys that can appear at most once in a layout, because [TextKeyboard] finds them by id
         */
        open val unique: Boolean get() = false

        /**
         * Percentage of the row width, `0f` means filling the remaining space
         */
        abstract val width: Float

        @Serializable
        @SerialName("alphabet")
        data class Alphabet(
            val main: String,
            val alt: String,
            override val width: Float = 0.1f,
            val variant: Variant = Variant.Normal
        ) : Key()

        @Serializable
        @SerialName("symbol")
        data class Symbol(
            val text: String,
            override val width: Float = 0.1f,
            val variant: Variant = Variant.Normal
        ) : Key()

        @Serializable
        @SerialName("layout-switch")
        data class LayoutSwitch(
            val label: String,
            val to: String = "",
            override val width: Float = 0.15f
        ) : Key()

        @Serializable
        @SerialName("comma")
        data class Comma(
            override val width: Float = 0.1f,
            val variant: Variant = Variant.Alternative
        ) : Key()

        @Serializable
        @SerialName("caps")
        data class Caps(override val width: Float = 0.15f) : Key() {
            override val unique get() = true
        }

        @Serializable
        @SerialName("backspace")
        data class Backspace(override val width: Float = 0.15f) : Key() {
            override val unique get() = true
        }

        @Serializable
        @SerialName("quickphrase")
        data class QuickPhrase(override val width: Float = 0.1f) : Key() {
            override val unique get() = true
        }

        @Serializable
        @SerialName("language")
        data class Language(override val width: Float = 0.1f) : Key() {
            override val unique get() = true
        }

        @Serializable
        @SerialName("space")
        data object Space : Key() {
            override val unique get() = true
            override val width get() = 0f
        }

        @Serializable
        @SerialName("return")
        data class Return(override val width: Float = 0.15f) : Key() {
            override val unique get() = true
        }
    }

    /**
     * @throws IllegalArgumentException if the layout cannot be displayed
     */
    fun validate() {
        require(version == VERSION) { "Unsupported version $version, expected $VERSION" }
        require(rows.isNotEmpty()) { "Layout has no rows" }
        val uniqueKeys = mutableSetOf<Class<out Key>>()
        rows.forEachIndexed { r, row ->
            require(row.isNotEmpty()) { "Row ${r + 1} is empty" }
            row.forEachIndexed { k, key ->
                val pos = "row ${r + 1} key ${k + 1}"
                if (key.unique) {
                    require(uniqueKeys.add(key.javaClass)) { "Duplicated ${key.javaClass.simpleName} at $pos" }
                }
                if (key !is Key.Space) {
                    require(key.width > 0f && key.width <= 1f) { "Invalid width ${key.width} at $pos" }
                }
                when (key) {
                    is Key.Alphabet -> {
                        require(key.main.isSingleCodePoint()) { "Invalid main \"${key.main}\" at $pos" }
                        require(key.alt.isSingleCodePoint()) { "Invalid alt \"${key.alt}\" at $pos" }
                    }
                    is Key.Symbol -> {
                        require(key.text.isSingleCodePoint()) { "Invalid text \"${key.text}\" at $pos" }
                    }
                    is Key.LayoutSwitch -> {
                        require(key.label.isNotEmpty()) { "Empty label at $pos" }
                    }
                    else -> {}
                }
            }
            // allow a small error caused by float addition
            require(row.sumOf { it.width.toDouble() } <= 1.0 + 1e-4) { "Row ${r + 1} is wider than the keyboard" }
        }
    }

    companion object {
        const val VERSION = 1

        // keys are sent to fcitx as one keysym, which can only hold one character
        private fun String.isSingleCodePoint() = isNotEmpty() && codePointCount(0, length) == 1
    }
}
