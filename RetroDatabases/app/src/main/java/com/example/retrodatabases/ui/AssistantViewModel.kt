package com.example.retrodatabases.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.retrodatabases.assistant.Assistant
import com.example.retrodatabases.data.AppDatabase
import com.example.retrodatabases.data.seed.seedYears
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One question and the assistant's answer to it. */
data class Exchange(val question: String, val answer: Assistant.Answer)

/**
 * Keeps the assistant's conversation. The catalogue is read from the database when the screen opens, and the search
 * index is built on a background thread when the first question is answered. Nothing is sent off the device.
 */
class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.get(application).libraryDao()

    private val assistant: Deferred<Assistant> = viewModelScope.async(Dispatchers.Default) {
        Assistant(
            databases = dao.allDatabases(),
            records = dao.allRecords(),
            years = seedYears,
        )
    }

    private val _exchanges = MutableStateFlow<List<Exchange>>(emptyList())
    val exchanges: StateFlow<List<Exchange>> = _exchanges.asStateFlow()

    /** Answers the question and adds the exchange to the conversation. Blank questions are ignored. */
    fun ask(question: String) {
        val text = question.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val answer = withContext(Dispatchers.Default) { assistant.await().answer(text) }
            _exchanges.value = _exchanges.value + Exchange(text, answer)
        }
    }
}
