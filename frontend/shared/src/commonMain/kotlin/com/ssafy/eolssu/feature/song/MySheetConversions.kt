package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.data.song.MySheetRepository
import com.ssafy.eolssu.data.song.SheetConversion
import com.ssafy.eolssu.data.song.SheetConversionLookup
import com.ssafy.eolssu.data.song.SheetConversionRepository
import com.ssafy.eolssu.data.song.SheetConversionRetry
import com.ssafy.eolssu.data.song.SheetConversionStage
import com.ssafy.eolssu.data.song.SheetConversionStatus
import com.ssafy.eolssu.data.song.SheetNotation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.exp

/**
 * 내 악보 목록과 각 악보의 변환 상태. 연습곡 고르기 '내 악보' 탭과 악보 추가(EDIT-01) 화면이 함께 쓴다.
 *
 * @property sheets 내 악보. 아직 받지 않았으면 null.
 * @property conversions 변환 상태 조회(SHT-10)·다시 시도(SHT-14)로 받은 상태.
 * @property retrying '다시 시도'를 보내는 중인 악보 ID.
 * @property notice 다시 시도 뒤 한 번 보일 안내.
 */
data class MySheetList(
    val sheets: List<MySheet>? = null,
    val conversions: Map<String, SheetConversion> = emptyMap(),
    val retrying: Set<String> = emptySet(),
    val notice: ConversionNotice? = null,
) {
    /**
     * 그 악보의 변환 상태. 변환 상태 조회(SHT-10)로 받은 값이 있으면 그것을, 없으면 목록(SHT-01)의 `conversionStatus`만 쓴다.
     * 불러오기로 만들지 않은 악보는 null이다.
     */
    fun conversionOf(sheet: MySheet): SheetConversion? =
        conversions[sheet.id] ?: SheetConversionStatus.fromCode(sheet.conversionStatus)?.let { SheetConversion(sheet.id, it) }

    /** 아직 변환 중이라 상태를 다시 물어야 하는 악보. */
    val convertingIds: List<String>
        get() = sheets.orEmpty().filter { conversionOf(it)?.status?.inProgress == true }.map { it.id }

    /** 목록에서 이미 실패한 악보도 원인별 안내를 위해 상세를 한 번 받는다. */
    val failedConversionIdsWithoutDetails: List<String>
        get() = sheets.orEmpty().filter {
            it.id !in conversions && SheetConversionStatus.fromCode(it.conversionStatus) == SheetConversionStatus.Failed
        }.map { it.id }

    /**
     * 새 목록을 받는다. 목록에서 빠진 악보의 변환 상태는 버리고,
     * 목록이 이미 끝났다(DONE·FAILED)고 하는데 받아 둔 상태가 변환 중이면 목록을 믿는다.
     */
    fun receive(received: List<MySheet>): MySheetList {
        val byId = received.associateBy { it.id }
        val kept = conversions.filter { (id, conversion) ->
            val listed = byId[id] ?: return@filter false
            val listedStatus = SheetConversionStatus.fromCode(listed.conversionStatus)
            !(conversion.status.inProgress && listedStatus != null && !listedStatus.inProgress)
        }
        return copy(sheets = received, conversions = kept, retrying = retrying.filterTo(mutableSetOf()) { it in byId })
    }
}

/** 변환 중인 단계. 목록 배지와 대기 화면이 같은 기준으로 문구를 고른다. */
sealed interface ConversionStep {
    data object Queued : ConversionStep

    /**
     * 쪽마다 인식하는 중. [pagesRead]는 서버가 읽은 쪽 수를 1 이상으로 줄 때만 있다.
     * 지금 서버는 인식 중 읽은 쪽 수를 0으로 두고 만들기로 넘어갈 때 한 번에 채우므로 보통 null이다.
     */
    data class Reading(val pageCount: Int, val pagesRead: Int?) : ConversionStep

    data object Building : ConversionStep
}

/** 변환 중이면 단계, 끝났으면(완료·실패) null. */
internal fun conversionStep(conversion: SheetConversion): ConversionStep? = when {
    !conversion.status.inProgress -> null
    conversion.status == SheetConversionStatus.Queued -> ConversionStep.Queued
    conversion.stage == SheetConversionStage.BuildingScore -> ConversionStep.Building
    else -> ConversionStep.Reading(
        pageCount = conversion.pageCount.coerceAtLeast(0),
        pagesRead = conversion.processedPageCount.takeIf { it in 1..conversion.pageCount },
    )
}

/**
 * 변환 진행 막대의 채움 비율(0~1). 서버는 퍼센트를 주지 않아 단계와 읽은 쪽 수로 어림한다(S15P31E102-881).
 * 대기 [PROGRESS_QUEUED] → 읽는 중 [PROGRESS_READING_START]~[PROGRESS_READING_END] → 만드는 중 [PROGRESS_BUILDING].
 * 읽는 중에는 서버가 준 읽은 쪽 수 비율과, 읽기 시작부터 지난 시간 [readingElapsedMs]의 예상치([readingEstimate]) 중 큰 쪽을 쓴다.
 * 끝난 변환(완료·실패)은 막대가 없어 null이다.
 */
internal fun conversionProgress(conversion: SheetConversion, readingElapsedMs: Long = 0): Float? =
    when (val step = conversionStep(conversion)) {
        null -> null
        ConversionStep.Queued -> PROGRESS_QUEUED
        ConversionStep.Building -> PROGRESS_BUILDING
        is ConversionStep.Reading -> {
            val byPages = step.pagesRead?.let { it.toFloat() / step.pageCount } ?: 0f
            val byTime = readingEstimate(readingElapsedMs, step.pageCount, conversion.notation)
            PROGRESS_READING_START + (PROGRESS_READING_END - PROGRESS_READING_START) * maxOf(byPages, byTime)
        }
    }

/**
 * 읽기 시작부터 [elapsedMs]가 지났을 때 읽기 구간을 얼마나 채웠을지의 어림값(0 ~ [READING_ESTIMATE_CAP]).
 * 쪽당 예상 시간에 쪽 수를 곱한 시간 상수로 천천히 다가가며, 서버가 끝났다고 하기 전에는 다 차지 않는다.
 * 쪽당 시간은 운영 서버 실측(오선보 1쪽 25~30초, S15P31E102-468)과 정간보 글자 층이 더 빠른 점에서 잡은 대략값이다.
 */
internal fun readingEstimate(elapsedMs: Long, pageCount: Int, notation: SheetNotation?): Float {
    if (elapsedMs <= 0) return 0f
    val perPage = if (notation == SheetNotation.Jeongganbo) READING_MS_PER_PAGE_JEONGGANBO else READING_MS_PER_PAGE_STAFF
    val expected = perPage * pageCount.coerceAtLeast(1)
    return ((1 - exp(-elapsedMs.toDouble() / expected)) * READING_ESTIMATE_CAP).toFloat()
}

private const val PROGRESS_QUEUED = 0.05f
private const val PROGRESS_READING_START = 0.1f
private const val PROGRESS_READING_END = 0.8f
private const val PROGRESS_BUILDING = 0.9f
private const val READING_ESTIMATE_CAP = 0.95
private const val READING_MS_PER_PAGE_STAFF = 28_000.0
private const val READING_MS_PER_PAGE_JEONGGANBO = 10_000.0

/** 변환 다시 시도 뒤 한 번 보일 안내. */
enum class ConversionNotice {
    /** 서버가 다시 시도할 수 없다고 했다(원본 미보관). 새 파일로 다시 불러와야 한다. */
    Reimport,

    /** 응답이 없거나 서버 오류라 다시 시도하지 못했다. */
    RetryFailed,
}

/**
 * 화면이 보이는 동안 변환 중인 내 악보의 상태(SHT-10)를 `pollAfterMs`마다 다시 묻고, 실패한 변환을 다시 시도(SHT-14)한다.
 * 상태는 화면 ViewModel이 들고 있고 [read]·[write]로 그 안의 [MySheetList]만 읽고 고친다.
 * 하나라도 끝나면(DONE·FAILED·404) 목록을 다시 받아 제목·장단·수정 시각을 맞춘다.
 * 이미 실패한 목록 항목은 원인을 한 번 받으며, 조회 실패는 재진입·목록 새로고침 때 다시 시도한다.
 */
internal class MySheetConversionWatcher(
    private val scope: CoroutineScope,
    private val mySheetRepository: MySheetRepository,
    private val repository: SheetConversionRepository?,
    private val read: () -> MySheetList,
    private val write: ((MySheetList) -> MySheetList) -> Unit,
) {
    private var watchJob: Job? = null
    private var watching = false
    private val failedDetailAttempts = mutableSetOf<String>()

    /** 계정이 바뀌었거나 목록을 새로 받기 전: 실패 상세를 다시 물을 수 있게 한다. */
    fun forgetFailedDetails() = failedDetailAttempts.clear()

    /** 새 목록을 받은 뒤: 목록에서 빠진 실패 기록을 지우고, 보이는 중이면 묻기를 이어 간다. */
    fun onSheetsReceived() {
        failedDetailAttempts.retainAll(read().failedConversionIdsWithoutDetails.toSet())
        start()
    }

    /** 화면이 보이면 묻기 시작한다. 가려졌다 다시 보이면 실패 상세도 다시 묻는다. */
    fun watch() {
        if (!watching) failedDetailAttempts.clear()
        watching = true
        start()
    }

    /** 화면이 가려지면 더 묻지 않는다. 다시 보이면 [watch]로 이어 간다. */
    fun stop() {
        watching = false
        watchJob?.cancel()
        watchJob = null
    }

    fun start() {
        val repository = repository ?: return
        if (!watching || !scope.isActive || watchJob?.isActive == true || idsToQuery().isEmpty()) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            while (isActive) {
                val ids = idsToQuery()
                if (ids.isEmpty()) break
                var finished = false
                // 서버가 준 간격 중 가장 짧은 것을 따른다. 아무도 주지 않았으면(응답 없음 포함) 기본 간격이다.
                var wait: Long? = null
                for (id in ids) {
                    val current = read()
                    if (current.sheets.orEmpty().none { it.id == id }) continue
                    val failureDetailsOnly = id in current.failedConversionIdsWithoutDetails
                    val lookup = try { repository.status(id) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { SheetConversionLookup.Unavailable }
                    if (!isActive) return@launch
                    if (read().sheets.orEmpty().none { it.id == id }) continue
                    if (failureDetailsOnly) failedDetailAttempts += id
                    when (lookup) {
                        is SheetConversionLookup.Found -> {
                            val conversion = lookup.conversion
                            write { it.copy(conversions = it.conversions + (id to conversion)) }
                            if (conversion.status.inProgress) {
                                val next = conversion.pollAfterMs ?: CONVERSION_POLL_DEFAULT_MS
                                wait = wait?.let { minOf(it, next) } ?: next
                            }
                            else if (!failureDetailsOnly || conversion.status != SheetConversionStatus.Failed) finished = true
                        }
                        SheetConversionLookup.NotFound -> {
                            write { it.copy(conversions = it.conversions - id) }
                            finished = true
                        }
                        SheetConversionLookup.Unavailable -> Unit
                    }
                }
                if (finished) mySheetRepository.refresh()
                if (idsToQuery().isEmpty()) break
                delay((wait ?: CONVERSION_POLL_DEFAULT_MS).coerceIn(CONVERSION_POLL_MIN_MS, CONVERSION_POLL_MAX_MS))
            }
        }
        watchJob = job
        job.invokeOnCompletion {
            if (watchJob === job) {
                watchJob = null
                start()
            }
        }
        job.start()
    }

    private fun idsToQuery(): List<String> = read().let { list ->
        list.convertingIds + list.failedConversionIdsWithoutDetails.filterNot { it in failedDetailAttempts }
    }

    /**
     * 실패한 변환의 '다시 시도'(SHT-14). 서버가 다시 시도할 수 있다고 한 악보만 보낸다.
     * 409면 원본이 없어 새 파일로 다시 불러와야 한다고 알리고, 받아 들이면 다시 상태를 묻기 시작한다.
     */
    fun retry(sheet: MySheet) {
        val repository = repository ?: return
        val list = read()
        if (!scope.isActive || sheet.id in list.retrying || list.conversionOf(sheet)?.retryable != true) return
        write { it.copy(retrying = it.retrying + sheet.id, notice = null) }
        scope.launch {
            val result = try { repository.retry(sheet.id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { SheetConversionRetry.Unavailable }
            write { current ->
                val done = current.copy(retrying = current.retrying - sheet.id)
                when (result) {
                    is SheetConversionRetry.Accepted -> done.copy(conversions = done.conversions + (sheet.id to result.conversion))
                    SheetConversionRetry.NotRetryable -> done.conversionOf(sheet)?.let { conversion ->
                        done.copy(conversions = done.conversions + (sheet.id to conversion.copy(retryable = false)), notice = ConversionNotice.Reimport)
                    } ?: done
                    SheetConversionRetry.NotFound -> done.copy(conversions = done.conversions - sheet.id)
                    SheetConversionRetry.Unavailable -> done.copy(notice = ConversionNotice.RetryFailed)
                }
            }
            when (result) {
                is SheetConversionRetry.Accepted -> start()
                SheetConversionRetry.NotFound -> mySheetRepository.refresh()
                else -> Unit
            }
        }
    }
}

/** 서버가 `pollAfterMs`를 주지 않을 때의 간격(SHT-10 진행 중 기본값과 같다). */
internal const val CONVERSION_POLL_DEFAULT_MS = 2_000L
private const val CONVERSION_POLL_MIN_MS = 1_000L
private const val CONVERSION_POLL_MAX_MS = 30_000L
