package com.ssafy.eolssu.feature.home

/**
 * 첫 화면에서 손대지 않고 기다리는 시간(ms). 이 시간이 지나면 무대를 펼쳐 카드를 보여 준다(S15P31E102-1013).
 * 아래 화살표의 왕복(1초씩 오르내림)을 두 번 보여 줘 스스로 내릴 기회를 먼저 주고,
 * 제목·버튼을 읽을 시간을 남기면서 '아무 일도 없는 화면'으로 느껴지기 전에 움직이도록 4초로 둔다.
 */
internal const val HomeAutoScrollDelayMillis = 4_000L

/**
 * 홈 첫 화면 자동 펼침의 진행 상태. 화면에 들어올 때마다 새로 시작하고, 한 번 펼치거나 취소하면 그 진입에서는 다시 움직이지 않는다.
 */
internal enum class HomeAutoScroll {
    /** 첫 화면에서 대기 중이다. */
    Waiting,

    /** 대기 시간이 지나 펼쳤다. */
    Fired,

    /** 대기 중에 사용자가 터치·스크롤해 취소했다. */
    Cancelled,

    /** 첫 화면이 아니거나 애니메이션 줄이기 설정이라 처음부터 움직이지 않는다. */
    Skipped,
    ;

    /** 터치·스크롤 같은 사용자 입력. 대기 중일 때만 취소한다. */
    fun onUserInput(): HomeAutoScroll = if (this == Waiting) Cancelled else this

    /** 첫 화면을 벗어났다(스크롤 위치가 0이 아니다). 자동 펼침이 시작된 뒤의 이동은 영향을 주지 않는다. */
    fun onScrolled(atTop: Boolean): HomeAutoScroll = if (atTop) this else onUserInput()

    /** 대기 시간이 다 지났다. 여전히 대기 중이면 펼친다. */
    fun onElapsed(): HomeAutoScroll = if (this == Waiting) Fired else this

    companion object {
        /**
         * 화면에 들어올 때의 상태. 다른 화면에서 돌아와 스크롤 위치가 복원됐으면 이미 본 화면이므로 움직이지 않는다.
         * 애니메이션 줄이기 설정에서는 순간 이동도 갑작스러우므로 자동 펼침을 끄고 화살표·스크롤에 맡긴다.
         */
        fun start(atTop: Boolean, reduceMotion: Boolean): HomeAutoScroll =
            if (atTop && !reduceMotion) Waiting else Skipped
    }
}
