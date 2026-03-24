package cz.hodiny.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object DebugLogger {
    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    fun log(tag: String, msg: String) {
        val line = "${LocalTime.now().format(fmt)} [$tag] $msg"
        _entries.update { (listOf(line) + it).take(200) }
    }

    fun clear() { _entries.value = emptyList() }
}
