package com.ssafy.eolssu.backend.feature.sheet

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** 유효한 작은 이미지. 형식 판별 테스트에서도 매직 바이트만 있는 가짜 파일을 쓰지 않는다. */
internal fun sheetImageBytes(format: String = "PNG"): ByteArray = ByteArrayOutputStream().use { output ->
    check(ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output))
    output.toByteArray()
}
