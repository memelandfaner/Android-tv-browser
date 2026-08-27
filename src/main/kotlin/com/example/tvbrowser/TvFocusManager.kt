package com.example.tvbrowser

import android.view.KeyEvent
import android.widget.EditText

class TvFocusManager(
    private val editUrl: EditText,
    private val isFullscreenActive: () -> Boolean,
    private val onToggleCursor: () -> Unit,
    private val onStartVoice: () -> Unit,
    private val onToggleBookmarks: () -> Unit,
    private val onToggleFullscreen: () -> Unit,
    private val onSubtitlesKey: () -> Unit,
    private val onServersKey: () -> Unit
) {

    fun handleTvKey(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.action != KeyEvent.ACTION_DOWN) return false

        when (keyCode) {
            // 🟢 Green Button / Menu (Keycode 82 or PROG_GREEN 184)
            KeyEvent.KEYCODE_MENU, 184, KeyEvent.KEYCODE_PROG_GREEN -> {
                if (isFullscreenActive()) {
                    onSubtitlesKey()
                } else {
                    editUrl.requestFocus()
                    editUrl.selectAll()
                }
                return true
            }

            // 🟡 Yellow Button (Keycode 185 or PROG_YELLOW) -> Toggle Cursor
            185, KeyEvent.KEYCODE_PROG_YELLOW -> {
                onToggleCursor()
                return true
            }

            // 🎙️ Dedicated Remote Mic Key / Voice Assist / Search / 🔴 Red Button (Keycode 183)
            KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_ASSIST,
            183, KeyEvent.KEYCODE_PROG_RED -> {
                if (isFullscreenActive()) {
                    onServersKey()
                } else {
                    onStartVoice()
                }
                return true
            }

            // 🔵 Blue Button (Keycode 186 or PROG_BLUE) / 'F' / Gamepad X -> Toggle Fullscreen
            186, KeyEvent.KEYCODE_PROG_BLUE, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_BUTTON_X -> {
                onToggleFullscreen()
                return true
            }
        }
        return false
    }
}

