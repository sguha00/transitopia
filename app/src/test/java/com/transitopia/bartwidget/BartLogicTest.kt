package com.transitopia.bartwidget

import com.transitopia.bartwidget.data.BartLogic
import com.transitopia.bartwidget.data.GtfsRtParser
import com.google.transit.realtime.GtfsRealtime
import org.junit.Assert.*
import org.junit.Test

class BartLogicTest {
    private val now = 1_700_000_000L
    // Trip A: 24TH -> ... -> EMBR (terminal). Trip B: 24TH -> ... -> DALY (wrong terminal).
    private val tripToEmbr = listOf(
        Triple("24TH", now + 300, 0L), Triple("16TH", now + 420, 0L), Triple("EMBR", now + 900, 0L))
    private val tripToDaly = listOf(
        Triple("24TH", now + 120, 0L), Triple("DALY", now + 1500, 0L))
    private val pastTrip = listOf(
        Triple("24TH", now - 60, 0L), Triple("EMBR", now + 600, 0L))

    @Test fun picksNextTrainToTerminalOnly() {
        val next = BartLogic.findNextTrain(listOf(tripToDaly, tripToEmbr, pastTrip), "24TH", "EMBR", now)
        assertNotNull(next); assertEquals(5L, next!!.etaMinutes); assertEquals(now + 300, next.departureEpochSec)
    }
    @Test fun returnsNullWhenNoMatchingTerminal() {
        assertNull(BartLogic.findNextTrain(listOf(tripToDaly), "24TH", "EMBR", now))
    }
    @Test fun returnsNullWhenAllInPast() {
        assertNull(BartLogic.findNextTrain(listOf(pastTrip), "24TH", "EMBR", now))
    }
    @Test fun parserMapsPlatformStopIdsToParents() {
        // Build a tiny FeedMessage with stop_id M60-1 (24TH platform) -> EMBR terminal.
        fun stu(stop: String, time: Long) = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
            .setStopId(stop).setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder().setTime(time).setDelay(0)).build()
        val tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
            .setTrip(GtfsRealtime.TripDescriptor.newBuilder().setTripId("1965"))
            .addStopTimeUpdate(stu("M60-1", now + 600)).addStopTimeUpdate(stu("M16-1", now + 1200)).build()
        val feed = GtfsRealtime.FeedMessage.newBuilder()
            .setHeader(GtfsRealtime.FeedHeader.newBuilder().setGtfsRealtimeVersion("1.0").setTimestamp(now))
            .addEntity(GtfsRealtime.FeedEntity.newBuilder().setId("1").setTripUpdate(tripUpdate).build())
            .build()
        val trips = GtfsRtParser.parse(feed, mapOf("M60-1" to "24TH", "M16-1" to "EMBR"))
        val next = BartLogic.findNextTrain(trips, "24TH", "EMBR", now)
        assertNotNull(next); assertEquals(10L, next!!.etaMinutes)
    }
    @Test fun matchesIntermediateDestination() {
        // Real-world case: Embarcadero -> 24th St, train continues to Daly City (24th is NOT its final stop).
        val through = listOf(
            Triple("EMBR", now + 240, 0L), Triple("MONT", now + 300, 0L), Triple("POWL", now + 360, 0L),
            Triple("CIVC", now + 420, 0L), Triple("16TH", now + 540, 0L), Triple("24TH", now + 660, 0L),
            Triple("GLEN", now + 780, 0L), Triple("DALY", now + 1200, 0L))
        val next = BartLogic.findNextTrain(listOf(through), "EMBR", "24TH", now)
        assertNotNull(next); assertEquals(4L, next!!.etaMinutes)
        // Opposite direction on the same trip must NOT match (24th is before Embarcadero here).
        assertNull(BartLogic.findNextTrain(listOf(through), "24TH", "EMBR", now))
    }
    @Test fun returnsNext3TrainsSorted() {
        fun trip(dep: Long) = listOf(Triple("EMBR", dep, 0L), Triple("24TH", dep + 600, 0L))
        val trips = listOf(trip(now + 900), trip(now + 300), trip(now + 600), trip(now + 1200), trip(now - 60))
        val next3 = BartLogic.findNextTrains(trips, "EMBR", "24TH", now, 3)
        assertEquals(listOf(5L, 10L, 15L), next3.map { it.etaMinutes })
        assertEquals(3, BartLogic.findNextTrains(trips, "EMBR", "24TH", now).size)
    }
    @Test fun shortNamesFitWidget() {
        assertEquals("24th St", BartLogic.shortName("24th Street / Mission"))
        assertEquals("SFO", BartLogic.shortName("San Francisco International Airport"))
        assertEquals("Embarcadero", BartLogic.shortName("Embarcadero"))
        assertEquals("Berryessa", BartLogic.shortName("Berryessa / North San Jose"))
        assertEquals("S. San Francisco", BartLogic.shortName("South San Francisco"))
    }
    @Test fun nextEtaShowsSecondsOnlyUnder10Min() {
        assertEquals("4:32", BartLogic.formatNextEta(272))
        assertEquals("9:59", BartLogic.formatNextEta(599))
        assertEquals("10 min", BartLogic.formatNextEta(600))
        assertEquals("14 min", BartLogic.formatNextEta(840))
        assertEquals("12 min", BartLogic.formatLaterEta(750))
    }
    @Test fun detailedCountsTotalForLastTrain() {
        fun trip(dep: Long) = listOf(Triple("EMBR", dep, 0L), Triple("24TH", dep + 600, 0L))
        val (trains, total) = BartLogic.findNextTrainsDetailed(listOf(trip(now + 300)), "EMBR", "24TH", now, 3)
        assertEquals(1, trains.size); assertEquals(1, total) // total==1 => Last Train
        val (t2, total2) = BartLogic.findNextTrainsDetailed(listOf(trip(now + 300), trip(now + 900)), "EMBR", "24TH", now, 3)
        assertEquals(2, t2.size); assertEquals(2, total2)
    }
    @Test fun formatEta() { assertEquals("Now", BartLogic.formatEta(0)); assertEquals("1 min", BartLogic.formatEta(1)); assertEquals("12 min", BartLogic.formatEta(12)) }
}