package com.ssafy.eolssu.core.audio

/**
 * 업로드를 기다리는 녹음 파일. 녹음기는 새 녹음을 시작할 때 자기 파일을 지우므로, 보낼 녹음은 그 밖으로 옮겨 둔다.
 * 플랫폼 구현은 `platform/audio`에 둔다.
 */
interface RecordingFiles {
    /** [path]의 녹음을 앱을 다시 켜도 남는 곳으로 옮기고 새 경로를 돌려준다. 옮기지 못하면 null. */
    fun keep(path: String): String?

    /** 녹음 파일을 지운다. 없으면 아무것도 하지 않는다. */
    fun delete(path: String)
}
