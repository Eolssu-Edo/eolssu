package com.ssafy.eolssu.core.audio

import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * 업로드를 기다리는 녹음 파일. 녹음기는 새 녹음을 시작할 때 자기 파일을 지우므로, 보낼 녹음은 그 밖으로 옮겨 둔다.
 * 플랫폼 구현은 `platform/audio`에 둔다.
 */
interface RecordingFiles {
    /**
     * [path]의 녹음을 앱을 다시 켜도 남는 보관 위치로 옮기고 새 경로를 돌려준다. 이미 보관 위치에 있으면 그 경로를 돌려준다.
     * 파일이 없거나 옮기지 못하면 null.
     */
    fun keep(path: String): String?

    /** [path]에 녹음 파일이 있는지. 녹음기 위치든 보관 위치든 묻는다. */
    fun exists(path: String): Boolean

    /**
     * 저장해 둔 [path]의 녹음이 지금 있는 경로. 없으면 null.
     * iOS는 앱을 업데이트하면 앱 폴더 경로가 바뀔 수 있어, 보관 위치의 같은 파일 이름으로 다시 찾는다.
     */
    fun locate(path: String): String? = path.takeIf(::exists)

    /** 녹음 파일을 지운다. 없으면 아무것도 하지 않는다. */
    fun delete(path: String)

    /** 보관 위치에서 [path]를 뺀 나머지 녹음을 지운다. 기록을 잃어 남은 파일을 치울 때 쓴다. */
    fun deleteKeptExcept(path: String?)

    /** 업로드할 [path]의 크기(바이트). 모르면 null. 파일 시스템이 없는 웹은 메모리의 녹음 크기를 준다. */
    fun size(path: String): Long? = SystemFileSystem.metadataOrNull(Path(path))?.size

    /** 업로드할 [path]를 처음부터 읽는다. 부를 때마다 새로 연다. */
    fun open(path: String): Source = SystemFileSystem.source(Path(path)).buffered()
}
