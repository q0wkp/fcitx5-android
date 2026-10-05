/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.fcitx.fcitx5.android.data.keyboard.KeyboardLayoutFile
import org.fcitx.fcitx5.android.data.keyboard.KeyboardLayoutFile.Key
import org.fcitx.fcitx5.android.data.keyboard.KeyboardLayoutFile.Variant
import org.junit.Assert
import org.junit.Test

class KeyboardLayoutFileTest {

    private fun String.toLayout() = Json.decodeFromString<KeyboardLayoutFile>(this)

    private fun layout(vararg rows: List<Key>) = KeyboardLayoutFile(KeyboardLayoutFile.VERSION, rows.toList())

    private fun assertInvalid(layout: KeyboardLayoutFile) {
        Assert.assertThrows(IllegalArgumentException::class.java) { layout.validate() }
    }

    // same as TextKeyboard.Layout
    private val builtin = """
        {
          "version": 1,
          "rows": [
            [
              { "type": "alphabet", "main": "Q", "alt": "1" },
              { "type": "alphabet", "main": "W", "alt": "2" },
              { "type": "alphabet", "main": "E", "alt": "3" },
              { "type": "alphabet", "main": "R", "alt": "4" },
              { "type": "alphabet", "main": "T", "alt": "5" },
              { "type": "alphabet", "main": "Y", "alt": "6" },
              { "type": "alphabet", "main": "U", "alt": "7" },
              { "type": "alphabet", "main": "I", "alt": "8" },
              { "type": "alphabet", "main": "O", "alt": "9" },
              { "type": "alphabet", "main": "P", "alt": "0" }
            ],
            [
              { "type": "alphabet", "main": "A", "alt": "@" },
              { "type": "alphabet", "main": "S", "alt": "*" },
              { "type": "alphabet", "main": "D", "alt": "+" },
              { "type": "alphabet", "main": "F", "alt": "-" },
              { "type": "alphabet", "main": "G", "alt": "=" },
              { "type": "alphabet", "main": "H", "alt": "/" },
              { "type": "alphabet", "main": "J", "alt": "#" },
              { "type": "alphabet", "main": "K", "alt": "(" },
              { "type": "alphabet", "main": "L", "alt": ")" }
            ],
            [
              { "type": "caps" },
              { "type": "alphabet", "main": "Z", "alt": "'" },
              { "type": "alphabet", "main": "X", "alt": ":" },
              { "type": "alphabet", "main": "C", "alt": "\"" },
              { "type": "alphabet", "main": "V", "alt": "?" },
              { "type": "alphabet", "main": "B", "alt": "!" },
              { "type": "alphabet", "main": "N", "alt": "~" },
              { "type": "alphabet", "main": "M", "alt": "\\" },
              { "type": "backspace" }
            ],
            [
              { "type": "layout-switch", "label": "?123" },
              { "type": "comma" },
              { "type": "language" },
              { "type": "space" },
              { "type": "symbol", "text": ".", "variant": "alternative" },
              { "type": "return" }
            ]
          ]
        }
    """.trimIndent()

    @Test
    fun builtin() {
        val layout = builtin.toLayout()
        layout.validate()
        Assert.assertEquals(listOf(10, 9, 9, 6), layout.rows.map { it.size })
        Assert.assertEquals(Key.Alphabet("Q", "1"), layout.rows[0][0])
        Assert.assertEquals(Key.Caps(0.15f), layout.rows[2][0])
        Assert.assertEquals(Key.LayoutSwitch("?123", "", 0.15f), layout.rows[3][0])
        Assert.assertEquals(Key.Comma(0.1f, Variant.Alternative), layout.rows[3][1])
        Assert.assertEquals(Key.Space, layout.rows[3][3])
        Assert.assertEquals(Key.Symbol(".", 0.1f, Variant.Alternative), layout.rows[3][4])
    }

    @Test
    fun customWidth() {
        // ЙЦУКЕН has 11 keys in the first row
        val row = "ЙЦУКЕНГШЩЗХ".map { Key.Alphabet(it.toString(), "1", width = 1f / 11) }
        layout(row).validate()
        assertInvalid(layout(row.map { it.copy(width = 0.1f) }))
        assertInvalid(layout(listOf(Key.Symbol(",", width = 0f))))
    }

    @Test
    fun keyText() {
        layout(listOf(Key.Alphabet("😀", "!"))).validate()
        assertInvalid(layout(listOf(Key.Alphabet("", "1"))))
        assertInvalid(layout(listOf(Key.Alphabet("Q", ""))))
        assertInvalid(layout(listOf(Key.Alphabet("ng", "1"))))
        assertInvalid(layout(listOf(Key.Symbol(""))))
        assertInvalid(layout(listOf(Key.LayoutSwitch(""))))
    }

    @Test
    fun uniqueKeys() {
        layout(listOf(Key.Comma(), Key.Comma())).validate()
        assertInvalid(layout(listOf(Key.Space), listOf(Key.Space)))
        assertInvalid(layout(listOf(Key.Caps()), listOf(Key.Caps(0.1f))))
    }

    @Test
    fun structure() {
        assertInvalid(KeyboardLayoutFile(2, listOf(listOf(Key.Space))))
        assertInvalid(layout())
        assertInvalid(layout(listOf()))
    }

    @Test
    fun unknownField() {
        Assert.assertThrows(SerializationException::class.java) {
            """{ "version": 1, "rows": [[{ "type": "space", "width": 0.5 }]] }""".toLayout()
        }
        Assert.assertThrows(SerializationException::class.java) {
            """{ "version": 1, "rows": [[{ "type": "emoji" }]] }""".toLayout()
        }
    }
}
