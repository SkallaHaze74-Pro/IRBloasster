package com.skallahaze.irbloasster.data

import com.skallahaze.irbloasster.model.DiagnosticDirection
import com.skallahaze.irbloasster.model.DiagnosticEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DiagnosticsLog(
    private val maxEntries: Int = 500
) {
    private val _entries = MutableStateFlow<List<DiagnosticEntry>>(emptyList())
    val entries: StateFlow<List<DiagnosticEntry>> = _entries.asStateFlow()

    @Synchronized
    fun add(
        direction: DiagnosticDirection,
        category: String,
        message: String
    ) {
        val entry = DiagnosticEntry(
            timestampMillis = System.currentTimeMillis(),
            direction = direction,
            category = category,
            message = redact(message)
        )
        _entries.value = (_entries.value + entry).takeLast(maxEntries)
    }

    fun info(category: String, message: String) =
        add(DiagnosticDirection.INFO, category, message)

    fun out(category: String, message: String) =
        add(DiagnosticDirection.OUT, category, message)

    fun incoming(category: String, message: String) =
        add(DiagnosticDirection.IN, category, message)

    fun warn(category: String, message: String) =
        add(DiagnosticDirection.WARN, category, message)

    fun error(category: String, message: String) =
        add(DiagnosticDirection.ERROR, category, message)

    fun clear() {
        _entries.value = emptyList()
    }

    private fun redact(value: String): String {
        return value
            .replace(
                Regex("""("client-key"\s*:\s*")[^"]+(")""", RegexOption.IGNORE_CASE)
            ) { match ->
                "${match.groupValues[1]}<redacted>${match.groupValues[2]}"
            }
            .replace(
                Regex("""(client[-_ ]?key\s*[=:]\s*)[^,\s}]+""", RegexOption.IGNORE_CASE)
            ) { match ->
                "${match.groupValues[1]}<redacted>"
            }
    }
}
