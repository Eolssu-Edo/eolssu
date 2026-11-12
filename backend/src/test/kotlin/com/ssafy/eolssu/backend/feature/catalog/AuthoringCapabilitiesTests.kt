package com.ssafy.eolssu.backend.feature.catalog

import com.ssafy.eolssu.backend.feature.catalog.application.AuthoringCapabilities
import com.ssafy.eolssu.backend.feature.catalog.domain.CatalogReferenceRepository
import com.ssafy.eolssu.backend.feature.sheet.application.SheetAuthoringValidation
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import kotlin.test.*

class AuthoringCapabilitiesTests {
    private val catalogs = AuthoringCapabilities(ReferenceFixture())
    private val validation = SheetAuthoringValidation(catalogs, JsonMapper.builder().build())

    @Test fun `range membership never supplies missing fingering evidence`() {
        val notes = catalogs.get(9007199254740993)!!.items
        assertTrue(notes[0].selectable)
        assertEquals("WITHIN_REFERENCE_RANGE", notes[1].physical)
        assertEquals("UNKNOWN", notes[1].physicalVerification)
        assertEquals(listOf("FINGERING_UNKNOWN"), notes[1].reasonCodes)
        assertFalse(notes[1].selectable)
    }

    @Test fun `an empty registration is still missing guidance and unknown notation is not selectable`() {
        val source = ReferenceFixture()
        val empty = object : CatalogReferenceRepository by source {
            override fun fingerings(instrumentId: Long, pitchId: Long?) = source.fingerings(instrumentId, pitchId).map { it.copy(imageUrl = null) }
        }
        assertEquals("UNKNOWN", AuthoringCapabilities(empty).get(9007199254740993)!!.items.first().fingering)
        val unknownGlyph = object : CatalogReferenceRepository by source {
            override fun pitches(lowSemitone: Int?, highSemitone: Int?) = source.pitches(lowSemitone, highSemitone).map { it.copy(yul = "미지원") }
        }
        val note = AuthoringCapabilities(unknownGlyph).get(9007199254740993)!!.items.first()
        assertEquals("REGISTERED", note.fingering)
        assertEquals("UNSUPPORTED", note.notation)
        assertFalse(note.selectable)
    }

    @Test fun `save policy identifies event and preserves no-fingering as unknown`() {
        val issues = assertNotNull(validation.validate(score("임", 1))).issues
        assertEquals("c0-j0-r0-u0", issues.single().eventId)
        assertEquals("c0-j0", issues.single().cellId)
        assertEquals(listOf("FINGERING_UNKNOWN"), issues.single().reasonCodes)
        assertNull(validation.validate(score("중", 0)))
    }

    @Test fun `actual reference frequency and changed instrument are revalidated`() {
        assertEquals(listOf("REFERENCE_TUNING_MISMATCH"), validation.validate(score("중", 0).replace("311.127", "349.228"))!!.issues.single().reasonCodes)
        assertEquals(listOf("INSTRUMENT_UNSUPPORTED"), validation.validate(score("중", 0).replace("DANSO", "UNKNOWN"))!!.issues.single().reasonCodes)
    }

    @Test fun `a written glyph must represent the note that will be synthesized`() {
        val mismatch = score("중", 0).replace("\"symbol\":\"중\"", "\"symbol\":\"황\"")
        assertEquals(listOf("NOTATION_PITCH_MISMATCH"), validation.validate(mismatch)!!.issues.single().reasonCodes)
    }

    private fun score(yul: String, octave: Int): String {
        val symbol = (if (octave == 1) "청" else "") + yul
        return """{"score":{"instrument":"DANSO"},"music":{"hwangHz":311.127},"columns":[{"cells":[{"id":"c0-j0","units":[{"row":0,"col":0,"eventId":"c0-j0-r0-u0","symbol":"$symbol"}]}]}],"events":[{"id":"c0-j0-r0-u0","cellId":"c0-j0","kind":"NOTE","yul":"$yul","octave":$octave}]}"""
    }
}
