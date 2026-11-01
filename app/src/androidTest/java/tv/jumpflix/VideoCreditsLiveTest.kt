// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import tv.jumpflix.data.*

/** Opt-in, read-only proof against the existing published catalog. */
class VideoCreditsLiveTest {
    @Test fun publishedFilmLoadsApprovedSpotsAndPublicSoundtrackWithoutAnAccount() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("creditsSmoke") == "true")
        val repository = VideoCreditsRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val credits = repository.load(CreditReference.from("movie:314")!!, force = true)
        assertNull(credits.spotsError)
        assertNull(credits.songsError)
        assertTrue(credits.spots.any { it.name == "Carcavelos Beach" && it.start == 208L && it.country == "Portugal" })
        assertTrue(credits.songs.any { it.title == "O Mar Apaga" && it.artist == "Banda Eddie" && it.spotifyUrl != null })
    }
}
