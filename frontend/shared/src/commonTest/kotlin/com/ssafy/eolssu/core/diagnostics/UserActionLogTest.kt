package com.ssafy.eolssu.core.diagnostics

import kotlin.test.*

class UserActionLogTest {
    @AfterTest fun reset() { UserActionLog.sink = null }
    @Test fun disabledDoesNotEvaluateOrAffectCallback() {
        var calls = 0
        UserActionLog.click(QaTarget.LOGIN_SUBMIT) { calls++ }()
        assertEquals(1, calls)
    }
    @Test fun callbackIsNotReportedAsFeatureSuccessAndSinkFailureCannotBreakIt() {
        val events = mutableListOf<UserActionEvent>()
        UserActionLog.sink = { events += it }
        UserActionLog.click(QaTarget.LOGIN_SUBMIT) {}()
        assertEquals(listOf(QaKind.CLICK, QaKind.CALLBACK_RETURNED), events.map { it.kind })
        assertTrue(events.all { it.outcome == null })
        assertEquals(1, events.map { it.interaction.id }.distinct().size)
        UserActionLog.sink = { error("sink offline") }
        var ran = false
        UserActionLog.click(QaTarget.LOGIN_SUBMIT) { ran = true }()
        assertTrue(ran)
    }
    @Test fun unavailableClickRemainsNullRatherThanBecomingAnEnabledNoOp() {
        assertNull(UserActionLog.nullableClick(QaTarget.BUTTON, null))
        UserActionLog.sink = {}
        assertNull(UserActionLog.nullableClick(QaTarget.BUTTON, null))
    }
    @Test fun dynamicDestinationTemplatesAreIdentifiedWithoutReadingArguments() {
        val events = mutableListOf<UserActionEvent>()
        UserActionLog.sink = { events += it }
        UserActionLog.screen("practice/{songId}?title={title}")
        assertEquals("practice_songid", events.last().screenId.name.lowercase())
        assertFalse(events.last().encode().contains("title"))
        UserActionLog.screen("unknown/private-user-title")
        assertEquals(QaScreen.UNIDENTIFIED, events.last().screenId)
    }
    @Test fun privacySchemaCannotAcceptInputTextOrLengths() {
        val output = UserActionEvent(QaKind.INPUT_CHANGED, QaTarget.LOGIN_PASSWORD,
            QaInteraction(4, false), QaScreen.AUTH_LOGIN, 5).encode()
        assertTrue(output.contains("input_changed"))
        for (forbidden in listOf("value", "length", "password123", "email@", "clipboard", "cookie", "token"))
            assertFalse(output.contains(forbidden))
    }
    @Test fun exactTouchScopeRestoresPreviousCorrelation() {
        UserActionLog.sink = {}
        UserActionLog.inInteraction(QaInteraction(31, false)) {
            assertEquals(QaInteraction(31, false), UserActionLog.capture())
            UserActionLog.inInteraction(QaInteraction(32, false)) { assertEquals(32, UserActionLog.capture().id) }
            assertEquals(31, UserActionLog.capture().id)
        }
        assertTrue(UserActionLog.capture().estimated)
    }
}
