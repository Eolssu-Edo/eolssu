package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.MySheetKind
import com.ssafy.eolssu.core.model.MySheetStatus
import com.ssafy.eolssu.core.model.PdfFile
import com.ssafy.eolssu.core.model.UploadedPdf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 내 악보. 와이어프레임 API 정리의 `GET /me/sheets`, `POST /me/sheets/pdf`에 대응한다.
 * 지금은 [SampleMySheetRepository]가 앱이 켜져 있는 동안만 악보를 들고 있다.
 */
interface MySheetRepository {
    /** 최근에 고치거나 올린 순. */
    val sheets: StateFlow<List<MySheet>>

    /** PDF를 올린다. [onProgress]로 0~1 진행률을 알리고, 올린 파일의 쪽 수를 돌려준다. */
    suspend fun uploadPdf(file: PdfFile, onProgress: (Float) -> Unit): UploadedPdf

    /** 올렸지만 아직 저장하지 않은 PDF. */
    suspend fun getUploadedPdf(uploadId: String): UploadedPdf?

    /** 올린 PDF를 내 악보에 저장한다. 맨 위에 보기 전용 악보로 들어간다. */
    suspend fun savePdf(uploadId: String, title: String, jangdanName: String?): MySheet
}

/** 파일 선택기를 붙이기 전까지 '파일에서 선택'이 고르는 예시 파일(와이어프레임 '상령산 정간보.pdf', 2.4MB). */
val SamplePdfFile = PdfFile(name = "상령산 정간보.pdf", sizeBytes = 2_516_582)

/**
 * 와이어프레임 예시 악보 3개로 시작한다. PDF 올리기는 [uploadStepMillis] 간격으로 10번에 나눠 진행률을 올리고,
 * 쪽 수는 파일과 관계없이 예시 값 [samplePageCount]를 돌려준다.
 */
class SampleMySheetRepository(
    private val now: () -> Long,
    private val uploadStepMillis: Long = 120,
    private val samplePageCount: Int = 3,
) : MySheetRepository {
    private val _sheets = MutableStateFlow(SampleSheets)
    override val sheets: StateFlow<List<MySheet>> = _sheets.asStateFlow()
    private val uploads = mutableMapOf<String, UploadedPdf>()
    private var nextId = 1

    override suspend fun uploadPdf(file: PdfFile, onProgress: (Float) -> Unit): UploadedPdf {
        for (step in 1..UPLOAD_STEPS) {
            delay(uploadStepMillis)
            onProgress(step / UPLOAD_STEPS.toFloat())
        }
        val upload = UploadedPdf("upload-${nextId++}", file.name, file.sizeBytes, samplePageCount)
        uploads[upload.id] = upload
        return upload
    }

    override suspend fun getUploadedPdf(uploadId: String): UploadedPdf? = uploads[uploadId]

    override suspend fun savePdf(uploadId: String, title: String, jangdanName: String?): MySheet {
        val upload = requireNotNull(uploads.remove(uploadId)) { "올리지 않은 PDF: $uploadId" }
        val sheet = MySheet(
            id = "sheet-${nextId++}",
            kind = MySheetKind.Pdf,
            status = MySheetStatus.ViewOnly,
            title = title,
            jangdanName = jangdanName,
            pageCount = upload.pageCount,
            updatedAt = KoreanDateTime.fromEpochMillis(now()),
        )
        _sheets.update { listOf(sheet) + it }
        return sheet
    }

    private companion object {
        const val UPLOAD_STEPS = 10
    }
}

private val SampleSheets = listOf(
    MySheet(
        "sample-jeongganbo", MySheetKind.Jeongganbo, MySheetStatus.Evaluable, "아리랑 (내 편곡)",
        jangdanName = SampleJangdans.Semachi.name, updatedAt = KoreanDateTime(2026, 10, 19, 21, 0), songId = "arirang",
    ),
    MySheet(
        "sample-midi", MySheetKind.Midi, MySheetStatus.NeedsReview, "아리랑_대금.m4a",
        pendingCells = 5, updatedAt = KoreanDateTime(2026, 10, 18, 20, 30),
    ),
    MySheet(
        "sample-pdf", MySheetKind.Pdf, MySheetStatus.ViewOnly, "상령산 정간보.pdf",
        pageCount = 3, updatedAt = KoreanDateTime(2026, 10, 17, 19, 0),
    ),
)
