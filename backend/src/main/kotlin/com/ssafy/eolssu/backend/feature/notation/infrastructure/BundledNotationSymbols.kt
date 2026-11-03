package com.ssafy.eolssu.backend.feature.notation.infrastructure

import com.ssafy.eolssu.backend.feature.catalog.domain.CatalogNotationSymbol
import org.springframework.core.io.ClassPathResource
import tools.jackson.databind.json.JsonMapper

/** Versioned teaching content. Exact matching prevents provenance being attached to edited DB content. */
object BundledNotationSymbols {
    private val entries: Map<String, Entry> by lazy {
        val json = ClassPathResource("notation-symbols/catalog.json").inputStream.use {
            JsonMapper.builder().build().readTree(it)
        }
        json.toList().associate { item ->
            val symbol = item.path("symbol").asString()
            symbol to Entry(symbol, item.path("name").asString(), item.path("description").asString(),
                item.path("asset").asString(), item.path("source").asString(), item.path("descriptionStatus").asString(),
                item.path("imageLicense").takeUnless { it.isNull }?.asString())
        }
    }

    fun match(symbol: CatalogNotationSymbol): Entry? = entries[symbol.symbol]?.takeIf {
        symbol.name == it.name && symbol.description == it.description && symbol.imageUrl == null
    }

    data class Entry(val symbol: String, val name: String, val description: String, val asset: String,
        val source: String, val descriptionStatus: String, val imageLicense: String?) {
        fun image(): ByteArray = ClassPathResource("notation-symbols/$asset.svg").inputStream.use { it.readAllBytes() }
    }
}
