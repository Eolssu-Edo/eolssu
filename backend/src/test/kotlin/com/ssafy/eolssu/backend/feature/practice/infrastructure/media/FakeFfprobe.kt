package com.ssafy.eolssu.backend.feature.practice.infrastructure.media

import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * 테스트용 가짜 ffprobe. 셸 없이 OS에 관계없이 실행되도록 테스트 JVM의 java로 띄운다.
 * 첫 인자로 동작을 고르고, 뒤에 붙는 ffprobe 인자는 무시한다.
 *
 * - `print <파일>`: 파일 내용을 그대로 출력하고 0으로 끝난다.
 * - `fail`: 표준 오류에 쓰고 1로 끝난다.
 * - `sleep <ms> <pid 파일>`: 자기 PID를 기록한 뒤 기다렸다가 `{}`를 출력한다.
 */
object FakeFfprobe {
    @JvmStatic
    fun main(args: Array<String>) {
        when (args[0]) {
            "print" -> print(Files.readString(Path.of(args[1])))
            "fail" -> {
                System.err.println("broken")
                exitProcess(1)
            }
            "sleep" -> {
                Files.writeString(Path.of(args[2]), ProcessHandle.current().pid().toString())
                Thread.sleep(args[1].toLong())
                print("{}")
            }
        }
    }

    fun command(vararg mode: String): List<String> = listOf(
        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-cp", System.getProperty("java.class.path"),
        FakeFfprobe::class.java.name,
        *mode,
    )
}
