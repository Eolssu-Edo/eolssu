package com.ssafy.eolssu.backend.feature.song.application

import com.ssafy.eolssu.backend.feature.song.domain.ModelAudio
import com.ssafy.eolssu.backend.feature.song.domain.ModelAudioRepository

class GetModelAudio(private val repository: ModelAudioRepository) {
    fun find(songId: Long): ModelAudio? = repository.findCurrent(songId)?.takeIf { it.hasValidMetadata() }
}
