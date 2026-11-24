package com.ssafy.eolssu.backend.feature.practice.infrastructure.media

import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * 테스트용 가짜 ffprobe·ffmpeg. 셸 없이 OS에 관계없이 실행되도록 테스트 JVM의 java로 띄운다.
 * 첫 인자로 동작을 고르고, 뒤에 붙는 ffprobe·ffmpeg 인자는 무시한다.
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
        // 가짜 프로세스에는 테스트 클래스와 Kotlin 런타임만 필요하다.
        // 전체 앱 classpath를 넘기면 의존성 증가 시 Windows 명령행 한도(CreateProcess 206)를 넘는다.
        "-cp", listOf(FakeFfprobe::class.java, Unit::class.java)
            .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }
            .distinct().joinToString(java.io.File.pathSeparator),
        FakeFfprobe::class.java.name,
        *mode,
    )
}
