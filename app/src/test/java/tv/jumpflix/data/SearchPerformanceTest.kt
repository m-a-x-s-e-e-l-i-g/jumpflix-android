// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import java.text.Normalizer
import java.util.Locale
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** The previous matcher is a compatibility reference and a before/after workload. */
class SearchPerformanceTest {
    @Volatile private var resultSink: Any? = null
    private val catalog = Catalog.parse(java.io.File("src/main/assets/catalog-seed.json").readText()).items
    private val people = buildPeopleIndex(catalog)
    private val songs = mapOf("movie:314" to listOf(MusicCredit("O Mar Apaga", "Banda Eddie")))
    private val queries = listOf("", "k", "ke", "kev", "kevin", "kevin f", "kevin franzen", "parkour", "párkour",
        "o", "o m", "o mar", "o mar apaga", "banda eddie", "peanutbutter eddie", "   \t ", "no such title 123")
    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    private fun oldFilmMatch(item: CatalogItem, query: String): Boolean = normalize(query).trim().split(Regex("\\s+")).all { word ->
        (listOf(item.title, item.description, item.creators.joinToString(" "), item.starring.joinToString(" ")) +
            songs[item.id].orEmpty().flatMap { listOf(it.title, it.artist) }).any { normalize(it).contains(word) }
    }
    private fun oldPersonMatch(person: Person, query: String): Boolean = query.isBlank() || query.trim().split(Regex("\\s+")).all { word ->
        personKey(person.name).removePrefix("~").contains(personKey(word).removePrefix("~"))
    }

    @Test fun searchesPreserveResultsAndReportTheSameCatalogWorkload() {
        fun filmResults(old: Boolean) = queries.map { query ->
            val terms = catalogSearchTerms(query)
            catalog.filter { if (old) oldFilmMatch(it, query) else it.matches(terms, songs[it.id].orEmpty()) }.map { it.id }
        }
        fun personResults(old: Boolean) = queries.map { query ->
            val terms = peopleSearchTerms(query)
            people.filter { if (old) oldPersonMatch(it, query) else it.matches(terms) }.map { it.key }
        }
        assertEquals(filmResults(true), filmResults(false))
        assertEquals(personResults(true), personResults(false))
        fun medianMs(block: () -> Any): Double {
            block()
            return List(5) { measureNanoTime { resultSink = block() } }.sorted()[2] / 1_000_000.0
        }
        println("SEARCH_WORKLOAD titles=${catalog.size} people=${people.size} queries=${queries.size}")
        println("FILMS referenceMs=${medianMs { filmResults(true) }} currentMs=${medianMs { filmResults(false) }}")
        println("PEOPLE referenceMs=${medianMs { personResults(true) }} currentMs=${medianMs { personResults(false) }}")
    }
}
