// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.ui

/** String routes survive recreation without retaining full catalog objects. */
internal data class BrowseTrail(val routes: List<String> = emptyList()) {
    val person: String? get() = routes.lastOrNull()?.takeIf { it.startsWith("person:") }?.removePrefix("person:")
    val film: String? get() = routes.lastOrNull()?.takeIf { it.startsWith("film:") }?.removePrefix("film:")
    fun openPerson(currentFilm: String?, key: String): BrowseTrail {
        if (person == key) return this
        val parents = if (routes.isEmpty() && currentFilm != null) listOf("film:$currentFilm") else routes
        return BrowseTrail(parents + "person:$key")
    }
    fun openFilm(id: String): BrowseTrail = BrowseTrail(routes + "film:$id")
    fun back(): BrowseTrail = BrowseTrail(routes.dropLast(1))
}
