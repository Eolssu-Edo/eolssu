package com.ssafy.eolssu.backend.feature.sheet.application

import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheet
import com.ssafy.eolssu.backend.feature.song.domain.JangdanPlaybackPattern

data class SheetAudioJangdan(val name: String,val bpmUnit: String,val pattern: JangdanPlaybackPattern)
fun interface SheetAudioJangdanResolver { fun resolve(sheet: UserSheet): SheetAudioJangdan? }
