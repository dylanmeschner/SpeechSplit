package no.srrlsm.speechsplit.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shared test cases (src/test/resources/vectors.json). The web version runs the same file
 * (tools/vectors_web_test.js), so the apps and the web version keep giving the same answers.
 */
class VectorsTest {
    private val vectors = JSONObject(javaClass.classLoader!!.getResource("vectors.json")!!.readText())

    private fun each(name: String, check: (JSONArray) -> Unit) {
        val list = vectors.getJSONArray(name)
        for (i in 0 until list.length()) check(list.getJSONArray(i))
    }

    @Test
    fun timesAreReadAndWrittenTheSame() {
        each("parseTime") { assertEquals("parseTime ${it.getString(0)}", it.getInt(1), parseTimeInput(it.getString(0))) }
        each("formatTime") { assertEquals(it.getString(1), formatTime(it.getInt(0))) }
        each("formatDiff") { assertEquals(it.getString(1), formatDiff(it.getInt(0))) }
        each("timeForInput") { assertEquals(it.getString(1), formatTimeForInput(it.getInt(0))) }
    }

    @Test
    fun partsAndRunsAreJudgedTheSame() {
        each("verdict") { assertEquals("verdict $it", it.getString(2), verdict(it.getInt(0), it.getInt(1)).name) }
        val runs = vectors.getJSONArray("runDiff")
        for (i in 0 until runs.length()) {
            val v = runs.getJSONObject(i)
            val run = PracticeRun(
                planId = "p", planTitle = "T", finishedAtEpochMs = 1,
                segments = listOf(RunSegment("a", "A", v.getInt("target"), v.getInt("actual"))),
                meeting = if (v.getBoolean("meeting")) MeetingKind.MIDWEEK else null,
                bufferSeconds = v.getInt("buffer"),
            )
            assertEquals("diff $v", v.getInt("diff"), run.diffSeconds)
            if (!v.getBoolean("meeting")) assertEquals("used $v", v.getInt("used"), run.bufferUsedSeconds)
        }
    }

    @Test
    fun weeksAreNamedTheSame() {
        each("weekLabel") { assertEquals(it.getString(2), weekLabel(it.getString(0), it.getString(1))) }
    }
}
