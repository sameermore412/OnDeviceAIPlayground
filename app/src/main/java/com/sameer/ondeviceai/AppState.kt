package com.sameer.ondeviceai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ThemeMode { System, Light, Dark }

data class Note(val id: Long, val text: String)

/**
 * App-wide state that the LLM's tools can change, so a tool call has a visible effect.
 * Kept in memory only; it resets when the app process dies.
 */
object AppState {
    private val _themeMode = MutableStateFlow(ThemeMode.System)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun addNote(text: String): Note {
        val note = Note(id = System.currentTimeMillis(), text = text)
        _notes.update { it + note }
        return note
    }

    fun clearNotes() {
        _notes.value = emptyList()
    }
}
