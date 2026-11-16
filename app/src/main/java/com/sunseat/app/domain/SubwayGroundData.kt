package com.sunseat.app.domain

/** UNKNOWN includes conflicting or missing source data; it must never imply sunlight. */
enum class GroundLevel { ABOVE, BELOW, UNKNOWN }

/** Bundled, offline station lookup. Keys always include the line to distinguish transfer platforms. */
class SubwayGroundData private constructor(private val stations: Map<Pair<String, String>, GroundLevel>) {
    fun level(line: String, station: String): GroundLevel =
        stations[normalizeLine(line) to normalizeStation(station)] ?: GroundLevel.UNKNOWN

    fun section(line: String, stops: List<String>): GroundLevel {
        if (stops.size < 2) return GroundLevel.UNKNOWN
        val levels = stops.map { level(line, it) }
        return when {
            levels.all { it == GroundLevel.ABOVE } -> GroundLevel.ABOVE
            levels.any { it == GroundLevel.BELOW } -> GroundLevel.BELOW
            else -> GroundLevel.UNKNOWN
        }
    }

    companion object {
        val bundled: SubwayGroundData by lazy {
            val stream = checkNotNull(SubwayGroundData::class.java.getResourceAsStream("/subway_ground.csv"))
            stream.bufferedReader().use { parse(it.readText()) }
        }

        internal fun parse(csv: String): SubwayGroundData {
            val entries = csv.lineSequence().drop(1).filter { it.isNotBlank() }.map { row ->
                val cells = row.split(',')
                require(cells.size == 3) { "Invalid station row" }
                (normalizeLine(cells[0]) to normalizeStation(cells[1])) to GroundLevel.valueOf(cells[2])
            }.groupBy({ it.first }, { it.second })
            return SubwayGroundData(entries.mapValues { (_, values) -> values.distinct().singleOrNull() ?: GroundLevel.UNKNOWN })
        }

        fun normalizeStation(name: String): String = name
            .replace(Regex("\\([^)]*\\)|（[^）]*）"), "")
            .replace(Regex("\\s+"), "").removeSuffix("역")

        private fun normalizeLine(name: String): String {
            val compact = name.replace(Regex("\\s+"), "")
                .removePrefix("수도권").removePrefix("서울지하철").removePrefix("서울")
            return when (compact) {
                "수인분당", "분당선", "수인선", "수인·분당선", "수인ㆍ분당선" -> "수인분당선"
                "경의중앙", "경의·중앙선", "경의ㆍ중앙선" -> "경의중앙선"
                else -> compact
            }
        }
    }
}
