package com.transitopia.bartwidget.data

data class Station(val id: String, val name: String, val platforms: List<String>, val isTerminal: Boolean)

data class NextTrain(
    val tripId: String,
    val departureEpochSec: Long,
    val etaMinutes: Long,
    val originParent: String,
    val terminalParent: String,
    val delaySec: Long
)

object BartLogic {
    // Short display names so "Berryessa/North San Jose -> SFO" fits a 3-wide widget.
    private val shortNames = mapOf(
        "24th Street / Mission" to "24th St", "16th Street / Mission" to "16th St",
        "12th Street / Oakland City Center" to "12th St", "19th Street Oakland" to "19th St",
        "Embarcadero" to "Embarcadero", "Montgomery Street" to "Montgomery", "Powell Street" to "Powell",
        "Civic Center / UN Plaza" to "Civic Center", "San Francisco International Airport" to "SFO",
        "Oakland International Airport" to "OAK", "Berryessa / North San Jose" to "Berryessa",
        "Warm Springs / South Fremont" to "Warm Springs", "Pleasant Hill / Contra Costa Centre" to "Pleasant Hill",
        "North Concord / Martinez" to "N. Concord", "Pittsburg / Bay Point" to "Pittsburg",
        "West Dublin / Pleasanton" to "W. Dublin", "Dublin / Pleasanton" to "Dublin",
        "El Cerrito Del Norte" to "El Cerrito N.", "El Cerrito Plaza" to "El Cerrito Plaza",
        "Downtown Berkeley" to "Berkeley", "Lake Merritt" to "Lake Merritt", "MacArthur" to "MacArthur",
        "South San Francisco" to "S. San Francisco", "Pittsburg Center" to "Pittsburg Ctr",
        "El Cerrito Plaza" to "El Cerrito Plz", "San Leandro" to "San Leandro", "Castro Valley" to "Castro Valley"
    )
    fun shortName(full: String): String =
        shortNames[full] ?: full.substringBefore('/').trim().replace(" Street", " St").replace("Station", "").trim()

    /** Next train: mm:ss countdown under 10 min, whole minutes otherwise. */
    fun formatNextEta(etaSec: Long): String = when {
        etaSec < 0 -> "Now"
        etaSec < 600 -> "${etaSec / 60}:${(etaSec % 60).toString().padStart(2, '0')}"
        else -> "${etaSec / 60} min"
    }

    fun formatLaterEta(etaSec: Long): String =
        if (etaSec < 0) "Now" else "${etaSec / 60} min"

    /**
     * Pure matching logic (unit-testable). A trip matches when the origin appears in its
     * ordered stops and the destination appears after it (may be intermediate).
     */
    fun findNextTrains(
        trips: List<List<Triple<String, Long, Long>>>,
        originParent: String,
        terminalParent: String,
        nowEpochSec: Long,
        count: Int = 3
    ): List<NextTrain> = findNextTrainsDetailed(trips, originParent, terminalParent, nowEpochSec, count).first

    /** Returns the next [count] trains plus the total number of matching trips left in the feed. */
    fun findNextTrainsDetailed(
        trips: List<List<Triple<String, Long, Long>>>,
        originParent: String,
        terminalParent: String,
        nowEpochSec: Long,
        count: Int = 3
    ): Pair<List<NextTrain>, Int> {
        require(originParent != terminalParent) { "Origin and terminal must differ" }
        val matches = mutableListOf<Triple<String, Long, Long>>()
        for (stops in trips) {
            if (stops.isEmpty()) continue
            val originIdx = stops.indexOfFirst { it.first == originParent }
            if (originIdx < 0) continue
            if (stops.indexOfFirst { it.first == terminalParent } <= originIdx) continue
            val stop = stops[originIdx]
            if (stop.second < nowEpochSec) continue
            matches.add(stop)
        }
        val sorted = matches.sortedBy { it.second }.distinctBy { it.second }
        return sorted.take(count).map { stop ->
            NextTrain("", stop.second, (stop.second - nowEpochSec) / 60, originParent, terminalParent, stop.third)
        } to sorted.size
    }

    fun findNextTrain(
        trips: List<List<Triple<String, Long, Long>>>,
        originParent: String,
        terminalParent: String,
        nowEpochSec: Long
    ): NextTrain? = findNextTrains(trips, originParent, terminalParent, nowEpochSec, 1).firstOrNull()

    fun formatEta(etaMinutes: Long): String = when {
        etaMinutes <= 0 -> "Now"
        etaMinutes == 1L -> "1 min"
        else -> "$etaMinutes min"
    }
}
