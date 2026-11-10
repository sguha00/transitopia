package com.transitopia.bartwidget.data

import com.google.transit.realtime.GtfsRealtime

object GtfsRtParser {
    /** Convert a parsed FeedMessage into per-trip ordered (parentStation, departure, delay) lists. */
    fun parse(feed: GtfsRealtime.FeedMessage, stopToParent: Map<String, String>): List<List<Triple<String, Long, Long>>> {
        val out = mutableListOf<List<Triple<String, Long, Long>>>()
        for (entity in feed.entityList) {
            if (!entity.hasTripUpdate()) continue
            val tu = entity.tripUpdate
            val stops = mutableListOf<Triple<String, Long, Long>>()
            for (stu in tu.stopTimeUpdateList) {
                val parent = stopToParent[stu.stopId] ?: continue
                val dep = when {
                    stu.hasDeparture() && stu.departure.hasTime() -> stu.departure.time
                    stu.hasArrival() && stu.arrival.hasTime() -> stu.arrival.time
                    else -> continue
                }
                val delay = when {
                    stu.hasDeparture() -> stu.departure.delay.toLong()
                    stu.hasArrival() -> stu.arrival.delay.toLong()
                    else -> 0L
                }
                stops.add(Triple(parent, dep, delay))
            }
            if (stops.isNotEmpty()) out.add(stops)
        }
        return out
    }
    fun parseBytes(bytes: ByteArray, stopToParent: Map<String, String>): List<List<Triple<String, Long, Long>>> =
        parse(GtfsRealtime.FeedMessage.parseFrom(bytes), stopToParent)
}
