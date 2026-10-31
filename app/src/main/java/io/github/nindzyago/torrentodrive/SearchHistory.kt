package io.github.nindzyago.torrentodrive

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray

/** Where a query came from: typed on the keyboard or spoken by voice. */
enum class QuerySource { Typed, Voice }

/** Two search histories (typed and spoken queries), newest first, no duplicates. */
class SearchHistory(context: Context) {
    private val prefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)

    val typed = mutableStateListOf<String>().apply { addAll(load(QuerySource.Typed)) }
    val voice = mutableStateListOf<String>().apply { addAll(load(QuerySource.Voice)) }

    fun add(source: QuerySource, phrase: String) {
        val text = phrase.trim()
        if (text.isEmpty()) return
        val list = list(source)
        list.removeAll { it.equals(text, ignoreCase = true) }
        list.add(0, text)
        while (list.size > LIMIT) list.removeAt(list.lastIndex)
        prefs.edit().putString(source.name, JSONArray(list.toList()).toString()).apply()
    }

    private fun list(source: QuerySource) = if (source == QuerySource.Typed) typed else voice

    private fun load(source: QuerySource): List<String> = runCatching {
        val arr = JSONArray(prefs.getString(source.name, "[]"))
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())

    private companion object {
        const val LIMIT = 12
    }
}
