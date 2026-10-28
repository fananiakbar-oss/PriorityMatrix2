package com.prioritymatrix.app.domain.logic

/** RFC 4180 style CSV reader/writer (quotes, escaped quotes, embedded commas and newlines). */
object CsvCodec {

    fun encode(rows: List<List<String>>): String = buildString {
        rows.forEach { row ->
            append(row.joinToString(",") { escape(it) })
            append("\r\n")
        }
    }

    fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
            value.startsWith(" ") || value.endsWith(" ")
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    fun decode(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val src = text.removePrefix("﻿")
        while (i < src.length) {
            val c = src[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < src.length && src[i + 1] == '"') {
                        field.append('"'); i++
                    } else inQuotes = false
                } else field.append(c)
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> { row.add(field.toString()); field.setLength(0) }
                    '\r' -> Unit
                    '\n' -> {
                        row.add(field.toString()); field.setLength(0)
                        rows.add(row); row = mutableListOf()
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows.filterNot { r -> r.all { it.isBlank() } }
    }
}
