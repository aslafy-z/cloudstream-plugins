package fr.m6plus.api

import org.junit.Assert.*
import org.junit.Test

class M6EpisodeOrderTest {
    private fun episodes(json: String) = M6CatalogClient(object : M6ApiClient() {
        override fun get(url: String, headers: Map<String, String>, cacheMs: Long) = json
    }).episodes("program", emptyList())

    @Test fun `reversed seasons and split parts are ordered with unique numbers per season`() {
        val result = episodes("""[
            {"id":"s21e2p1","type":"vi","title":"Épisode 2 - Partie 1","clips":[{"product":{"season":21,"episode":2,"part_number":1}}]},
            {"id":"s21e1p2","type":"vi","title":"Épisode 1 - Partie 2","clips":[{"product":{"season":21,"episode":1,"part_number":2}}]},
            {"id":"bonus","type":"vc","title":"Extrait","clips":[{"product":{"season":21,"episode":1,"part_number":1}}]},
            {"id":"s21e1p1","type":"vi","title":"Épisode 1 - Partie 1","clips":[{"product":{"season":21,"episode":1,"part_number":1}}]},
            {"id":"s20e1p2","type":"vi","clips":[{"product":{"season":20,"episode":1,"part_number":2}}]},
            {"id":"s20e1p1","type":"vi","clips":[{"product":{"season":20,"episode":1,"part_number":1}}]}
        ]""")
        assertEquals(listOf("s20e1p1", "s20e1p2", "s21e1p1", "s21e1p2", "s21e2p1", "bonus"), result.map { it.id })
        assertEquals(listOf(1, 2, 1, 2, 3, 4), result.map { it.displayNumber })
        assertEquals(result.size, result.map { it.season to it.displayNumber }.distinct().size)
        assertEquals("Épisode 1 - Partie 2", result[3].title)
    }

    @Test fun `series with one file per episode keep official numbers even when earlier replays expired`() {
        val result = episodes("""[
            {"id":"e48","clips":[{"product":{"season":2,"episode":48}}]},
            {"id":"e47","clips":[{"product":{"season":2,"episode":47}}]}
        ]""")
        assertEquals(listOf("e47", "e48"), result.map { it.id })
        assertEquals(listOf(47, 48), result.map { it.displayNumber })
    }

    @Test fun `unnumbered programmes use first broadcast instead of replay publication date`() {
        val result = episodes("""[
            {"id":"new","publication_date":"2026-10-21 12:00:00","clips":[{"product":{"first_diffusion":"2026-10-21 11:00:00"}}]},
            {"id":"old","publication_date":"2026-10-22 12:00:00","clips":[{"product":{"first_diffusion":"2026-10-20 11:00:00"}}]}
        ]""")
        assertEquals(listOf("old", "new"), result.map { it.id })
        assertEquals(listOf(1, 2), result.map { it.displayNumber })
    }

    @Test fun `playlist parts are retained and ordered using clip metadata`() {
        val result = episodes("""[{"id":"playlist","type":"playlist","clips":[
            {"video_id":"part2","title":"Partie 2","product":{"season":1,"episode":1,"part_number":2}},
            {"video_id":"part1","title":"Partie 1","product":{"season":1,"episode":1,"part_number":1}}
        ]}]""")
        assertEquals(listOf("part1", "part2"), result.map { it.id })
        assertEquals(listOf(1, 2), result.map { it.displayNumber })
    }
}
