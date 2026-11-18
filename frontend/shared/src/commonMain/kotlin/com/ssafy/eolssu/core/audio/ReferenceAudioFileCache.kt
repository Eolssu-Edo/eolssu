package com.ssafy.eolssu.core.audio

/**
 * 기본음·요성 예시음 파일(S15P31E102-1005). 예시음은 3초 남짓의 작은 파일이라 누를 때마다 서버를 거쳐 다시 받지 않고
 * 내용 해시로 확인한 기기 파일을 다시 쓴다. 모범 연주 파일과 지우는 순서가 섞이지 않게 자기 폴더를 쓴다.
 */
class ReferenceAudioFileCache(private val files: ModelAudioFileCache) {
    /** null이면 받지 못했거나 이 형식을 저장하지 않는다. 호출한 쪽은 원격 주소로 재생한다. */
    suspend fun fileFor(url: String, sha256: String, contentType: String): LocalAudioFile? =
        files.fileFor(url, sha256, contentType)

    companion object {
        const val DIRECTORY = "reference-audio-v1"
        /** 예시음 하나가 100KB 안팎이라 한 악기의 예시음을 다 담을 만큼 둔다. */
        const val MAX_FILES = 128
        const val MAX_FILE_BYTES = 1024L * 1024
    }
}
