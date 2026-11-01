// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import tv.jumpflix.data.PersonRepository

class PeopleLiveTest {
    @Test fun publicProfileLinksLoadWithoutAnAccount() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("peopleSmoke") == "true")
        val result = PersonRepository(InstrumentationRegistry.getInstrumentation().targetContext).links("chris-cobas", force = true)
        assertNull(result.error)
        assertTrue("chris.cobas" in result.instagram)
    }
}
