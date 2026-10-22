package fr.m6plus.model

internal data class M6Program(val id: String, val title: String, val poster: String?, val description: String?)
internal data class M6Episode(val id: String, val title: String, val poster: String?, val description: String?, val season: Int?)
internal data class M6Playback(val manifest: String, val token: String, val subtitles: List<String>)
