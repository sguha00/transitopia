package com.transitopia.bartwidget.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BartRepository(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()

    fun loadStations(): List<Station> {
        val json = context.assets.open("stations.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(json).getJSONArray("stations")
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Station(
                id = o.getString("id"), name = o.getString("name"),
                platforms = List(o.getJSONArray("platforms").length()) { o.getJSONArray("platforms").getString(it) },
                isTerminal = o.getBoolean("isTerminal")
            )
        }.sortedBy { it.name }
    }

    /** Next [count] trains plus total matching trips left in the feed (for Last Train detection). */
    suspend fun nextTrainsDetailed(origin: String, terminal: String, count: Int = 3): Result<Pair<List<NextTrain>, Int>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val stations = loadStations()
                val stopToParent = stations.flatMap { s -> s.platforms.map { it to s.id } }.toMap()
                val req = Request.Builder()
                    .url("https://api.bart.gov/gtfsrt/tripupdate.aspx")
                    .header("Accept", "application/x-protobuf")
                    .build()
                val bytes = client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    resp.body!!.bytes()
                }
                val trips = GtfsRtParser.parseBytes(bytes, stopToParent)
                val now = System.currentTimeMillis() / 1000
                BartLogic.findNextTrainsDetailed(trips, origin, terminal, now, count)
            }
        }

    suspend fun nextTrains(origin: String, terminal: String, count: Int = 3): Result<List<NextTrain>> =
        nextTrainsDetailed(origin, terminal, count).map { it.first }

    suspend fun nextTrain(origin: String, terminal: String): Result<NextTrain?> =
        nextTrains(origin, terminal, 1).map { it.firstOrNull() }
}
