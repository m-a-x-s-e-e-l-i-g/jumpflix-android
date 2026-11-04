// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import android.content.Context
import java.io.File
import tv.jumpflix.BuildConfig

class CatalogRepository(context: Context) {
    private val client = CatalogClient(File(context.filesDir, "catalog-v1.json"),
        { context.assets.open("catalog-seed.json").bufferedReader().use { it.readText() } },
        BuildConfig.CATALOG_BASE_URL, "Jumpflix-Android/${BuildConfig.VERSION_NAME}")

    suspend fun saved(): Catalog? = client.saved()
    suspend fun refresh(): Catalog = client.refresh()
    suspend fun series(slug: String): CatalogItem = client.series(slug)
}
