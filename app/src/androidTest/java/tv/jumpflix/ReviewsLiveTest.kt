// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import tv.jumpflix.data.ReviewRepository

class ReviewsLiveTest {
    @Test fun publishedReviewsAreReadableWithoutSigningIn() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("reviewsSmoke") == "true")
        val page = ReviewRepository(InstrumentationRegistry.getInstrumentation().targetContext).page("movie:320")
        assertTrue(page.reviews.any { it.id == 170L && it.body.isNotBlank() })
    }
}
