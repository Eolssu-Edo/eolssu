package com.ssafy.eolssu.core.network

/**
 * 로그인 쿠키를 여러 실행 단위가 함께 쓸 때 필요한 장치들(S15P31E102-1264). 웹은 모든 탭이 같은 쿠키를 쓰고
 * 페이지를 열 때마다 앱이 새로 시작하므로 웹 플랫폼 모듈만 등록한다. 앱(Android·iOS)은 등록하지 않아 지금 동작을 유지한다.
 */

/**
 * 탭 사이의 토큰 갱신 잠금과 갱신 세대. 서버는 이미 회전된 리프레시 토큰이 다시 오면 탈취로 보고 그 회원의 토큰을 모두 폐기하므로,
 * 두 탭이 같은 쿠키로 동시에 갱신하면 계정 전체가 로그아웃된다. 갱신은 [withLock] 안에서 하고, 성공하면 [advance]로 세대를 올려
 * 기다리던 다른 탭이 다시 갱신하지 않고 새 쿠키로 원래 요청을 재전송하게 한다.
 */
interface SharedRefreshLock {
    /** 모든 탭이 함께 보는 마지막 갱신 세대. */
    val generation: Long

    /** 다른 탭과 겹치지 않게 [block]을 실행한다. 잠금을 쓸 수 없는 브라우저에서는 그냥 실행한다. */
    suspend fun <T> withLock(block: suspend () -> T): T

    /** [withLock] 안에서 갱신에 성공했을 때 부른다. */
    fun advance()
}

/** 다른 탭이 로그인 회원을 바꾸면(로그아웃·다른 계정 로그인) 이 탭도 따르게 한다. 앱 시작 때 한 번 부른다. */
fun interface OtherTabSignIns {
    fun follow(member: SignedInMember)
}

/**
 * '이번 실행'의 범위. 자동 로그인을 끈 로그인은 다음 실행에서 끝내야 하는데, 앱은 프로세스 하나가 한 번의 실행이고
 * 웹은 새로고침·새 탭이 아니라 브라우저를 닫았다 연 것이 새 실행이다.
 */
fun interface LaunchSession {
    /** 이번 실행에서 처음 불렸고 새 실행이면 true. 같은 실행에서 다시 부르면 false다. */
    fun claim(): Boolean
}

/** 앱의 실행 범위: 프로세스 안에서 처음 한 번만 true다. 같은 프로세스에서 화면이 다시 만들어져도(태블릿 회전 등) 다시 답하지 않는다. */
object ProcessLaunch : LaunchSession {
    private var claimed = false

    override fun claim(): Boolean {
        if (claimed) return false
        claimed = true
        return true
    }

    /** 테스트가 새 실행을 흉내 낼 때 쓴다. */
    internal fun reset() {
        claimed = false
    }
}
