// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import java.text.Normalizer
import java.util.Locale
import org.json.JSONArray

private val personMarks = Regex("[\\u0300-\\u036f]")
private val slugSeparators = Regex("[^a-z0-9]+")
private val personWords = Regex("\\s+")

/** Matches the website's canonical /people/<slug> normalization. */
fun personSlug(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFKD)
    .replace(personMarks, "").lowercase(Locale.ROOT).replace("&", " and ")
    .replace(slugSeparators, "-").trim('-')

fun personKey(name: String): String = personSlug(name).ifBlank { "~${name.trim().lowercase(Locale.ROOT)}" }

internal fun peopleSearchTerms(query: String): List<String> = if (query.isBlank()) emptyList()
    else query.trim().split(personWords).map { personKey(it).removePrefix("~") }

data class Person(val key: String, val name: String, val created: Set<String>, val featured: Set<String>) {
    val titles: Set<String> get() = created + featured
    val role: String get() = when {
        created.isNotEmpty() && featured.isNotEmpty() -> "Creator & athlete"
        created.isNotEmpty() -> "Creator"
        else -> "Athlete"
    }
    fun matches(query: String): Boolean = matches(peopleSearchTerms(query))
    // The index key already contains the canonical normalized name.
    internal fun matches(terms: List<String>): Boolean = terms.all { key.removePrefix("~").contains(it) }
}

fun buildPeopleIndex(items: List<CatalogItem>): List<Person> {
    data class Credits(val names: LinkedHashMap<String, Int> = linkedMapOf(), val created: MutableSet<String> = linkedSetOf(), val featured: MutableSet<String> = linkedSetOf())
    val index = linkedMapOf<String, Credits>()
    items.distinctBy { it.id }.forEach { item ->
        fun add(names: List<String>, creator: Boolean) {
            names.map(String::trim).filter(String::isNotBlank).distinct().forEach { name ->
                val record = index.getOrPut(personKey(name)) { Credits() }
                record.names[name] = (record.names[name] ?: 0) + 1
                (if (creator) record.created else record.featured).add(item.id)
            }
        }
        add(item.creators, true)
        add(item.starring, false)
    }
    return index.map { (key, credits) -> Person(key, credits.names.maxBy { it.value }.key, credits.created.toSet(), credits.featured.toSet()) }
        .sortedBy { it.key }
}

data class PersonLinks(val instagram: List<String> = emptyList(), val error: String? = null)

internal fun parsePersonLinks(json: String): PersonLinks {
    val rows = JSONArray(json)
    val handles = rows.optJSONObject(0)?.optJSONArray("instagram_handles") ?: return PersonLinks()
    return PersonLinks((0 until handles.length()).mapNotNull { index ->
        val raw = (handles.opt(index) as? String)?.trim()?.lowercase(Locale.ROOT) ?: return@mapNotNull null
        val handle = raw.replace(Regex("^https?://(?:www\\.)?instagram\\.com/"), "").trimStart('@').substringBefore('/').substringBefore('?').substringBefore('#')
        handle.takeIf { it.matches(Regex("[a-z0-9._]{1,30}")) }
    }.distinct())
}
