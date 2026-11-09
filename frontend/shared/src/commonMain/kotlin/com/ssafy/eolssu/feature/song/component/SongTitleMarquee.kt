package com.ssafy.eolssu.feature.song.component

import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier

/** 곡 이름이 한 줄에 다 들어가지 않으면 왼쪽으로 흘려 끝까지 보이고, 끝에 닿으면 처음부터 다시 흘린다. 다 들어가면 움직이지 않는다. */
internal fun Modifier.songTitleMarquee(): Modifier = basicMarquee(iterations = Int.MAX_VALUE)
