package com.ssafy.eolssu.backend.feature.catalog.domain

val REFERENCE_AUDIO_DATASETS = setOf("danso", "sogeum", "jeongak-daegeum", "sanjo-daegeum")
private val HASH = Regex("[0-9a-f]{64}")
fun isReferenceAudioId(value: String) = HASH.matches(value)

/** An existing labeled recording, not a pitch or a scoring standard. */
data class ReferenceAudioAsset(
    val assetId: String, val datasetId: String, val labelId: String,
    val category: String, val technique: String, val registerName: String, val yulmyeong: String, val displayLabel: String,
    val objectKey: String, val contentType: String, val sha256: String, val byteSize: Long, val durationMs: Int,
    val sampleRateHz: Int, val channels: Short, val reviewStatus: String, val eligibleForNoteScoring: Boolean,
    val metadataJson: String, val pitchId: Long?,
) {
    init {
        require(datasetId in REFERENCE_AUDIO_DATASETS && isReferenceAudioId(assetId) && HASH.matches(sha256))
        require(objectKey == "reference-audio/v1/$datasetId/$assetId/$sha256.m4a")
        require(contentType == "audio/mp4" && byteSize > 0 && durationMs > 0 && sampleRateHz > 0 && channels in 1..8)
        require(reviewStatus in setOf("inferred", "reviewed", "rejected"))
        require(!eligibleForNoteScoring || reviewStatus == "reviewed")
        require(pitchId == null || (pitchId > 0 && category == "기본음계"))
    }
}

data class ReferenceAudioFilter(val datasetId: String, val category: String? = null, val technique: String? = null,
    val registerName: String? = null, val yulmyeong: String? = null, val pitchId: Long? = null, val relatedPitchId: Long? = null)

/** SEQUENCE is observed anchor order; other roles have no temporal index. */
data class ReferenceAudioPitchRelation(val assetId: String, val role: String, val anchorIndex: Int,
    val pitchId: Long, val evidencePath: String)

interface ReferenceAudioRepository {
    fun published(filter: ReferenceAudioFilter): List<ReferenceAudioAsset>
    fun published(assetId: String): ReferenceAudioAsset?
    fun pitchRelations(assetIds: List<String>): List<ReferenceAudioPitchRelation>
}

fun interface ReferenceAudioReadUrls { fun readUrl(objectKey: String): String }
class ReferenceAudioUnavailable : RuntimeException("Reference audio storage unavailable")
