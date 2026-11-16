package com.ssafy.eolssu.core.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/** Only code-owned targets and typed metadata can cross this boundary. Never accepts text/URLs/errors. */
enum class QaKind {
    TOUCH_START, TOUCH_END, TOUCH_CANCEL, GESTURE, KEY_ACTION, CLICK, CALLBACK_ENTERED, CALLBACK_RETURNED,
    INPUT_CHANGED, INPUT_FOCUS, INPUT_BLUR, INPUT_COMMIT, INPUT_VALIDATION, SCREEN_ENTER, SCREEN_LEAVE,
    LIFECYCLE, MODAL_OPEN, MODAL_CLOSE, LOGIN_ATTEMPT, LOGIN_RESULT, CONTENT_SELECTED,
    AUDIO_REQUESTED, AUDIO_STATE, RECORDING_STATE, HTTP_STARTED, HTTP_FINISHED,
}
enum class QaOutcome { READY, FAILED, CANCELLED, INVALID, LOCKED, UNAVAILABLE, STARTED, COMPLETED, STOPPED, PAUSED, LOADING, INTERRUPTED }
enum class QaGesture { TAP, DOUBLE_TAP, LONG_PRESS, DRAG, SCROLL, MULTI_TOUCH, CANCEL }
data class QaInteraction(val id: Int, val estimated: Boolean)
class QaInteractionContext(val interaction: QaInteraction) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<QaInteractionContext>
}

data class UserActionEvent(
    val kind: QaKind,
    val target: QaTarget,
    val interaction: QaInteraction,
    val screenId: QaScreen,
    val ms: Long,
    val outcome: QaOutcome? = null,
    val requestId: String? = null,
    val status: Int? = null,
    val durationMs: Long? = null,
    val gesture: QaGesture? = null,
    val x: Int? = null,
    val y: Int? = null,
    val dx: Int? = null,
    val dy: Int? = null,
    val pointers: Int? = null,
    val content: QaContent? = null,
    val eventAt: kotlin.time.Instant = Clock.System.now(),
) {
    fun encode(): String = buildJsonObject {
        put("schema_version", 2)
        put("kind", kind.name.lowercase())
        put("target", target.name.lowercase())
        put("screen_id", screenId.name.lowercase())
        put("interaction_id", interaction.id)
        put("correlation", if (interaction.estimated) "estimated" else "exact")
        put("ms", ms)
        put("event_at", eventAt.toString())
        outcome?.let { put("outcome", it.name.lowercase()) }
        requestId?.let { put("request_id", it) }
        status?.let { put("status", it) }
        durationMs?.let { put("duration_ms", it.coerceAtLeast(0)) }
        gesture?.let { put("gesture", it.name.lowercase()) }
        x?.let { put("x", it.coerceIn(0, 1000)) }; y?.let { put("y", it.coerceIn(0, 1000)) }
        dx?.let { put("dx", it.coerceIn(-1000, 1000)) }; dy?.let { put("dy", it.coerceIn(-1000, 1000)) }
        pointers?.let { put("pointers", it.coerceIn(1, 32)) }
        content?.let { put("content_id", it.name.lowercase()) }
    }.toString()
}

/** Installed exclusively by the Android QA entry point; other builds retain a no-op sink. */
object UserActionLog {
    @Volatile var sink: ((UserActionEvent) -> Unit)? = null
    @Volatile private var active: QaInteraction? = null
    @Volatile private var pointer: QaInteraction? = null
    fun pointerScope(interaction: QaInteraction?) { pointer = interaction }
    @Volatile private var last = QaInteraction(0, true)
    @Volatile var screenId: QaScreen = QaScreen.UNIDENTIFIED
        private set
    private val ids = MutableStateFlow(0)
    private var clickDepth = 0
    private var inputDepth = 0
    fun newInteraction(): QaInteraction = QaInteraction(ids.updateAndGet { it + 1 }, false)
    fun now(): Long = PerformanceLog.now()
    fun capture(): QaInteraction {
        if (last.id == 0 && sink != null) last = newInteraction()
        return active ?: last.copy(estimated = true)
    }
    fun <T> inInteraction(interaction: QaInteraction, block: () -> T): T {
        val previous = active
        active = interaction; last = interaction
        try { return block() } finally { active = previous }
    }
    fun event(kind: QaKind, target: QaTarget, outcome: QaOutcome? = null,
        interaction: QaInteraction = capture(), content: QaContent? = null) {
        if (sink == null) return
        emit(UserActionEvent(kind, target, interaction, screenId, PerformanceLog.now(), outcome, content = content))
    }
    fun emit(event: UserActionEvent) { runCatching { sink?.invoke(event) } }
    fun click(target: QaTarget, block: () -> Unit): () -> Unit = if (sink == null) block else { ->
        if (sink == null) block() else {
            val interaction = active ?: pointer?.copy(estimated = true) ?: newInteraction()
            inInteraction(interaction) {
                event(if (clickDepth++ == 0) QaKind.CLICK else QaKind.CALLBACK_ENTERED, target)
                try { block(); event(QaKind.CALLBACK_RETURNED, target) } finally { clickDepth-- }
            }
        }
    }
    fun nullableClick(target: QaTarget, block: (() -> Unit)?): (() -> Unit)? = block?.let { click(target, it) }
    fun <T> change(target: QaTarget, block: (T) -> Unit): (T) -> Unit = if (sink == null) block else { value ->
        if (sink == null) block(value) else {
            val interaction = active ?: pointer?.copy(estimated = true) ?: newInteraction()
            inInteraction(interaction) {
                if (inputDepth++ == 0) event(QaKind.INPUT_CHANGED, target)
                try { block(value) } finally { inputDepth-- }
            }
        }
    }
    /** Route templates only, never navigation arguments such as title, email, path or recording key. */
    fun screen(template: String?) {
        if (sink == null) return
        val next = when (template?.substringBefore('?')) {
            "analysis/{songId}/{bpm}" -> QaScreen.ANALYSIS_SONGID_BPM
            "auth/link/{provider}" -> QaScreen.AUTH_LINK_PROVIDER
            "auth/login" -> QaScreen.AUTH_LOGIN
            "auth/oauth/{provider}" -> QaScreen.AUTH_OAUTH_PROVIDER
            "auth/password-reset" -> QaScreen.AUTH_PASSWORD_RESET
            "auth/password-reset/password" -> QaScreen.AUTH_PASSWORD_RESET_PASSWORD
            "auth/profile" -> QaScreen.AUTH_PROFILE
            "auth/signup" -> QaScreen.AUTH_SIGNUP
            "auth/signup/consent" -> QaScreen.AUTH_SIGNUP_CONSENT
            "auth/signup/password" -> QaScreen.AUTH_SIGNUP_PASSWORD
            "auth/social-signup/profile" -> QaScreen.AUTH_SOCIAL_SIGNUP_PROFILE
            "auth/terms/{agreement}" -> QaScreen.AUTH_TERMS_AGREEMENT
            "compare/{recordId}" -> QaScreen.COMPARE_RECORDID
            "growth" -> QaScreen.GROWTH
            "guide/fingering" -> QaScreen.GUIDE_FINGERING
            "guide/reading" -> QaScreen.GUIDE_READING
            "home" -> QaScreen.HOME
            "learn" -> QaScreen.LEARN
            "learn/stages" -> QaScreen.LEARN_STAGES
            "learn/stages/{stage}" -> QaScreen.LEARN_STAGES_STAGE
            "my/legal/{document}" -> QaScreen.MY_LEGAL_DOCUMENT
            "my/link/{provider}" -> QaScreen.MY_LINK_PROVIDER
            "my/nickname" -> QaScreen.MY_NICKNAME
            "my/password" -> QaScreen.MY_PASSWORD
            "my/profile-image" -> QaScreen.MY_PROFILE_IMAGE
            "my/sources" -> QaScreen.MY_SOURCES
            "my/withdraw" -> QaScreen.MY_WITHDRAW
            "practice/{songId}" -> QaScreen.PRACTICE_SONGID
            "records" -> QaScreen.RECORDS
            "records/{recordId}/evaluation" -> QaScreen.RECORDS_RECORDID_EVALUATION
            "result/{evaluationId}" -> QaScreen.RESULT_EVALUATIONID
            "sheet-edit/conversion/{id}" -> QaScreen.SHEET_EDIT_CONVERSION_ID
            "sheet-edit/edit" -> QaScreen.SHEET_EDIT_EDIT
            "sheet-edit/import/{kind}" -> QaScreen.SHEET_EDIT_IMPORT_KIND
            "sheet-edit/import/{kind}/result" -> QaScreen.SHEET_EDIT_IMPORT_KIND_RESULT
            "sheet-edit/new" -> QaScreen.SHEET_EDIT_NEW
            "sheet-edit/pick" -> QaScreen.SHEET_EDIT_PICK
            "sheet-edit/view/{id}" -> QaScreen.SHEET_EDIT_VIEW_ID
            "songs" -> QaScreen.SONGS
            "songs/{songId}" -> QaScreen.SONGS_SONGID
            "weak/{source}/evaluate" -> QaScreen.WEAK_SOURCE_EVALUATE
            "weak/{source}/result/{sessionId}" -> QaScreen.WEAK_SOURCE_RESULT_SESSIONID
            "weak/{source}/sections" -> QaScreen.WEAK_SOURCE_SECTIONS
            else -> QaScreen.UNIDENTIFIED
        }
        if (next == screenId) return
        event(QaKind.SCREEN_LEAVE, QaTarget.NAVIGATION)
        screenId = next
        event(QaKind.SCREEN_ENTER, QaTarget.NAVIGATION)
    }
}

/** Content labels are fixed curriculum values, never asset IDs, URLs, filenames or user titles. */
enum class QaContent { DANSO, SOGEUM, DAEGEUM_JEONGAK, DAEGEUM_SANJO, LOW, MID, HIGH,
    EACH, SPLIT, HOLD, REST, LOW_HWANG, HWANG, HIGH_HWANG, BASIC, YOSEONG,
    UNIDENTIFIED, TAE, JUNG, IM, MU, NAM, GO, HYEOP, CHEONG_JUNG, CHEONG_IM, CHEONG_MU, CHEONG_HWANG, CHEONG_TAE, TAK_IM, TAK_NAM, TAK_MU, CHEONG_GO, CHEONG_NAM, CHEONG_HYEOP, JUNG_CHEONG_HWANG, JUNG_CHEONG_TAE, JUNG_CHEONG_GO, JUNG_CHEONG_JUNG, JUNG_CHEONG_IM,
    LEARN_STAGE_1, LEARN_STAGE_2, LEARN_STAGE_3, LEARN_STAGE_4, LEARN_STAGE_5, LEARN_STAGE_6, LEARN_STAGE_7, LEARN_STAGE_8, LEARN_STAGE_9,
    READING_INTRO, READING_ORDER, READING_LENGTH, READING_PITCH, READING_SIGIMSAE, READING_SIGIMSAE_MORE }

enum class QaScreen { UNIDENTIFIED, ANALYSIS_SONGID_BPM, AUTH_LINK_PROVIDER, AUTH_LOGIN, AUTH_OAUTH_PROVIDER, AUTH_PASSWORD_RESET, AUTH_PASSWORD_RESET_PASSWORD, AUTH_PROFILE, AUTH_SIGNUP, AUTH_SIGNUP_CONSENT, AUTH_SIGNUP_PASSWORD, AUTH_SOCIAL_SIGNUP_PROFILE, AUTH_TERMS_AGREEMENT, COMPARE_RECORDID, GROWTH, GUIDE_FINGERING, GUIDE_READING, HOME, LEARN, LEARN_STAGES, LEARN_STAGES_STAGE, MY_LEGAL_DOCUMENT, MY_LINK_PROVIDER, MY_NICKNAME, MY_PASSWORD, MY_PROFILE_IMAGE, MY_SOURCES, MY_WITHDRAW, PRACTICE_SONGID, RECORDS, RECORDS_RECORDID_EVALUATION, RESULT_EVALUATIONID, SHEET_EDIT_CONVERSION_ID, SHEET_EDIT_EDIT, SHEET_EDIT_IMPORT_KIND, SHEET_EDIT_IMPORT_KIND_RESULT, SHEET_EDIT_NEW, SHEET_EDIT_PICK, SHEET_EDIT_VIEW_ID, SONGS, SONGS_SONGID, WEAK_SOURCE_EVALUATE, WEAK_SOURCE_RESULT_SESSIONID, WEAK_SOURCE_SECTIONS }
