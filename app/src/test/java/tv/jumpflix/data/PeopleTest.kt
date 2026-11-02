// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.jumpflix.ui.BrowseTrail

class PeopleTest {
    @get:Rule val directory = TemporaryFolder()
    private fun sample() = Catalog.parse(java.io.File("src/main/assets/catalog-seed.json").readText()).items.first()

    @Test fun `canonical names combine roles without duplicating films or matching different people`() {
        val first = sample().copy(id = "movie:1", creators = listOf("Kévin Franzén", "Kevin Franzen"), starring = listOf("Kevin Franzén", "William"))
        val second = first.copy(id = "series:2", creators = listOf("Will"), starring = listOf("Kevin Franzen"))
        val people = buildPeopleIndex(listOf(first, first, second))
        val kevin = people.single { it.key == "kevin-franzen" }
        assertEquals(setOf("movie:1"), kevin.created)
        assertEquals(setOf("movie:1", "series:2"), kevin.featured)
        assertEquals(2, kevin.titles.size)
        assertEquals("Creator & athlete", kevin.role)
        assertEquals(3, people.size)
        assertEquals(setOf("series:2"), people.single { it.key == "will" }.titles)
        assertEquals(setOf("movie:1"), people.single { it.key == "william" }.titles)
        assertTrue(kevin.matches(""))
        assertTrue(kevin.matches("Franzen kevin"))
        assertFalse(kevin.matches("Kevin another"))
        assertEquals("a-and-b", personSlug(" A & B "))
        val international = buildPeopleIndex(listOf(first.copy(creators = listOf("A & B", "李明"), starring = emptyList())))
        assertTrue(international.single { it.name == "A & B" }.matches(peopleSearchTerms("a & b")))
        assertTrue(international.single { it.name == "李明" }.matches(peopleSearchTerms("李")))
        assertNotEquals(personKey("李明"), personKey("王伟"))
    }

    @Test fun `bundled athlete and filmmaker credits build real filmographies`() {
        val catalog = Catalog.parse(java.io.File("src/main/assets/catalog-seed.json").readText())
        val people = buildPeopleIndex(catalog.items)
        val kevinFilm = catalog.items.first { it.slug == "kevin-franzen-2026" }
        assertTrue(kevinFilm.id in people.single { it.key == "kevin-franzen" }.featured)
        assertTrue(kevinFilm.id in people.single { it.key == "lester-castro-nilsson" }.created)
        assertTrue(people.all { it.titles.isNotEmpty() })
    }

    @Test fun `social links accept only normalized Instagram handles and missing profiles are valid`() {
        val links = parsePersonLinks("""[{"instagram_handles":["@Real.Person","https://www.instagram.com/REAL.PERSON/?x=1",null,{},"https://evil.example/test","bad/../path","valid_handle"]}]""")
        assertEquals(listOf("real.person", "bad", "valid_handle"), links.instagram)
        assertTrue(parsePersonLinks("[]").instagram.isEmpty())
    }

    @Test fun `public profile requests are scoped cached and retain links on failure`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""[{"instagram_handles":["chris.cobas"]}]"""))
            val repository = PersonRepository(directory.root, server.url("").toString().trimEnd('/'), "public-key", OkHttpClient())
            val links = repository.links("chris-cobas")
            assertEquals(listOf("chris.cobas"), links.instagram)
            val request = server.takeRequest()
            assertEquals("eq.chris-cobas", request.requestUrl!!.queryParameter("slug"))
            assertEquals("instagram_handles", request.requestUrl!!.queryParameter("select"))
            assertNull(request.getHeader("Authorization"))
            assertEquals("public-key", request.getHeader("apikey"))
            assertEquals(links, repository.links("chris-cobas"))
            assertEquals(1, server.requestCount)
            server.enqueue(MockResponse().setResponseCode(503))
            val stale = repository.links("chris-cobas", force = true)
            assertEquals(links.instagram, stale.instagram)
            assertNotNull(stale.error)
            assertTrue(repository.links("../invalid").instagram.isEmpty())
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun `profile film and nested profile navigation returns through every parent`() {
        var trail = BrowseTrail().openPerson("movie:1", "creator").openFilm("movie:2").openPerson("movie:2", "athlete")
        assertEquals("athlete", trail.person)
        trail = trail.back()
        assertEquals("movie:2", trail.film)
        trail = trail.back()
        assertEquals("creator", trail.person)
        trail = trail.back()
        assertEquals("movie:1", trail.film)
        assertTrue(trail.back().routes.isEmpty())
        val search = BrowseTrail().openPerson(null, "athlete")
        assertEquals(search, search.openPerson(null, "athlete"))
        assertTrue(search.back().routes.isEmpty())
    }
}
