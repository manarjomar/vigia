package app.vigia.alarmas.domain

import java.time.DayOfWeek
import java.time.LocalDate

object RuleCodec {
    fun encodeDays(days: Set<DayOfWeek>): String =
        days.map { it.value }.sorted().joinToString(",")

    fun decodeDays(text: String): Set<DayOfWeek> =
        text.split(",").mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .map { DayOfWeek.of(it) }
            .toSet()

    fun encodeIds(ids: Set<Long>): String = ids.sorted().joinToString(",")

    fun decodeIds(text: String): Set<Long> =
        text.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()

    fun encodeKeywords(keywords: List<String>): String = keywords.joinToString("\n")

    fun decodeKeywords(text: String): List<String> =
        text.split("\n", ",").map { it.trim() }.filter { it.isNotEmpty() }

    fun encodeRanges(ranges: List<DateRange>): String =
        ranges.joinToString(";") { "${it.start}..${it.end}" }

    fun decodeRanges(text: String): List<DateRange> =
        text.split(";").mapNotNull { item ->
            val parts = item.split("..")
            if (parts.size != 2) return@mapNotNull null
            runCatching {
                DateRange(LocalDate.parse(parts[0]), LocalDate.parse(parts[1]))
            }.getOrNull()
        }
}
