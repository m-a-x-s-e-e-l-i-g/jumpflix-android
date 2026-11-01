// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.stats

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UserStatsTest {
    @Test fun `recorded time is formatted without inventing elapsed watch sessions`() {
        assertEquals("0m", statsDuration(-1))
        assertEquals("<1m", statsDuration(30))
        assertEquals("59m", statsDuration(3599))
        assertEquals("25h 1m", statsDuration(90060))
    }
    @Test fun `empty catalogs and repeated watch entries keep progress within bounds`() {
        assertEquals(0f, catalogFraction(0, 0))
        assertEquals(1f, catalogFraction(12, 10))
        assertEquals(.5f, catalogFraction(5, 10))
        assertEquals(0f, catalogFraction(-1, 10))
    }
    @Test fun `invalid averages and negative aggregate counts stay out of the UI`() {
        val stats = StatsOverview.parse(JSONObject("""{"ratings_count":-1,"watched_items":-2,"average_rating":11}"""))
        assertEquals(0, stats.watched)
        assertEquals(0, stats.ratings)
        assertNull(stats.averageRating)
    }
}
