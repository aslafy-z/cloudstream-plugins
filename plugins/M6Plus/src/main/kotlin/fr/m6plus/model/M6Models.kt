package fr.m6plus.model

internal data class M6Program(val id: String, val title: String, val poster: String?, val description: String?)
internal data class M6Episode(
    val id: String, val title: String, val poster: String?, val description: String?, val season: Int?,
    val episode: Int? = null, val part: Int? = null, val airedAt: String? = null,
    val isExtract: Boolean = false, val displayNumber: Int = 0,
)
internal data class M6Playback(val manifest: String, val token: String, val subtitles: List<String>)
