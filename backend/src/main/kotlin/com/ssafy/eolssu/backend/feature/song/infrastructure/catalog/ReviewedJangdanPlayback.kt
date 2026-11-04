package com.ssafy.eolssu.backend.feature.song.infrastructure.catalog

import com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence.JangdanRow
import com.ssafy.eolssu.backend.feature.song.domain.*
import java.security.MessageDigest
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** Only explicitly reviewed, byte-independent score identities receive accompaniment. */
class ReviewedJangdanPlayback(
    private val json: JsonMapper,
    private val findJangdan: (Long) -> JangdanRow?,
    manifest: JsonNode = requireNotNull(ReviewedJangdanPlayback::class.java.getResourceAsStream("/catalog/jangdan-playback.json")).use { json.readTree(it) },
) : SongJangdanPlaybackResolver {
    private val scores: Map<Long, JsonNode>
    private val patterns: Map<Long, JsonNode>

    init {
        require(manifest.path("schemaVersion").intValue() == 1)
        require(manifest.path("fingerprintFormat").asString() == "typed-tree-sha256-v1")
        val rows = manifest.path("scores").toList()
        require(rows.all { it.path("status").asString() in setOf("READY", "WITHHELD") && it.path("reason").asString().isNotBlank() })
        require(rows.map { it.path("songId").asString() }.distinct().size == rows.size)
        scores = rows.filter { it.path("status").asString() == "READY" }
            .associateBy { it.path("songId").asString().toLong() }
        val patternRows = manifest.path("patterns").toList()
        require(patternRows.map { it.path("jangdanId").asString() }.distinct().size == patternRows.size)
        patterns = patternRows.associateBy { it.path("jangdanId").asString().toLong() }
        for (review in scores.values) {
            require(review.path("scoreVersion").intValue() > 0 && review.path("defaultBpm").intValue() in 30..400)
            require(review.path("startTick").longValue() >= 0 && review.path("cycleTicks").longValue() > 0)
            val pattern = requireNotNull(patterns[review.path("jangdanId").asString().toLong()]).path("pattern")
            require(pattern.path("beats").isArray && pattern.path("group").isArray)
            require(pattern.path("beats").size() > 0 && pattern.path("beats").all { it.isString })
            require(pattern.path("group").all { it.isIntegralNumber && it.intValue() > 0 })
            require(pattern.path("group").sumOf { it.intValue() } == pattern.path("beats").size())
            require(review.path("cycleTicks").longValue() % pattern.path("beats").size() == 0L)
        }
    }

    override fun resolve(score: PublishedSongScore): ScoreJangdanPlayback? {
        val review = scores[score.songId] ?: return null
        if (score.scoreVersion != review.path("scoreVersion").intValue() ||
            score.defaultBpm != review.path("defaultBpm").intValue() || score.bpmUnit != review.path("bpmUnit").asString()) return null
        val source = json.readTree(score.scoreJson)
        val identity = source.path("score")
        if (identity.path("sourceId").asString() != review.path("sourceId").asString() ||
            identity.path("scoreVersion").intValue() != score.scoreVersion ||
            fingerprint(source) != review.path("scoreFingerprint").asString()) return null
        val id = review.path("jangdanId").asString().toLong()
        val expected = requireNotNull(patterns[id])
        val actual = findJangdan(id) ?: return null
        if (actual.id != id || actual.name != expected.path("name").asString() || actual.bpmUnit != expected.path("bpmUnit").asString() ||
            json.readTree(actual.pattern) != expected.path("pattern")) return null
        val pattern = expected.path("pattern")
        return ScoreJangdanPlayback(id.toString(), actual.name, review.path("startTick").longValue(), review.path("cycleTicks").longValue(),
            JangdanPlaybackPattern(pattern.path("beats").toList().map { it.asString() }, pattern.path("group").toList().map { it.intValue() }))
    }

    companion object {
        /** Stable across MySQL object-key ordering, whitespace and equivalent JSON number spellings. */
        fun fingerprint(node: JsonNode): String {
            val bytes = buildString { appendCanonical(node) }.toByteArray(Charsets.UTF_8)
            return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        }

        private fun StringBuilder.appendCanonical(node: JsonNode) {
            when {
                node.isObject -> {
                    append('{')
                    node.properties().sortedBy { it.key }.forEach { (key, value) -> appendString(key); appendCanonical(value) }
                    append('}')
                }
                node.isArray -> { append('['); node.forEach { appendCanonical(it) }; append(']') }
                node.isString -> appendString(node.asString())
                node.isNumber -> append('d').append(node.decimalValue().stripTrailingZeros().toPlainString()).append(';')
                node.isBoolean -> append(if (node.asBoolean()) 't' else 'f')
                node.isNull -> append('n')
                else -> error("Unsupported JSON node")
            }
        }

        private fun StringBuilder.appendString(value: String) {
            append('s').append(value.toByteArray(Charsets.UTF_8).size).append(':').append(value)
        }
    }
}
