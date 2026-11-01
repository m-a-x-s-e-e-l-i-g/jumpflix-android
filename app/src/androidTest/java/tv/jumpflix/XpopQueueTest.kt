// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import tv.jumpflix.xpop.WatchedItem
import tv.jumpflix.xpop.XpopWatchQueue

class XpopQueueTest {
    @Test fun offlineCompletionsStayWithTheirAccountAcrossRestarts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "qa-xpop-queue"
        context.getSharedPreferences(namespace, 0).edit().clear().commit()
        try {
            val item = WatchedItem("series:18:ep:video", "episode", 85, 100)
            val queue = XpopWatchQueue(context, namespace)
            queue.add("account-a", item); queue.add("account-b", item)
            queue.add("account-a", item)
            val restored = XpopWatchQueue(context, namespace)
            assertEquals(listOf(item), restored.forOwner("account-a"))
            assertEquals(listOf(item), restored.forOwner("account-b"))
            assertTrue(restored.forOwner("signed-out").isEmpty())
            restored.remove("account-a", item.mediaId)
            assertTrue(restored.forOwner("account-a").isEmpty())
            assertEquals(listOf(item), restored.forOwner("account-b"))
        } finally { context.getSharedPreferences(namespace, 0).edit().clear().commit() }
    }
}
