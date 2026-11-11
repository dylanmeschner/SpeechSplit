package no.srrlsm.speechsplit.core

// ============================================================================
// MEETING MODE
// For the chairman: the whole meeting as one timed plan, part by part.
// The midweek program changes every week. It's published on the website as
// docs/meetings/<lang>.json (made from the meeting workbook with tools/workbook_to_json.py)
// and saved on the device, so meeting mode also works offline.
// The weekend meeting is the same every week.
// ============================================================================

const val MEETINGS_URL = WEBSITE_URL + "meetings/"
val MEETING_LANGUAGES = listOf("no", "en", "de")

/** Fixed ids, so all runs of a kind of meeting share one history. */
const val MIDWEEK_MEETING_ID = "meeting-midweek"
const val WEEKEND_MEETING_ID = "meeting-weekend"

/** The midweek meeting is 1:40 of program in 1:45: 5 minutes to spare (counsel after student parts, etc.). */
const val MIDWEEK_BUFFER_SECONDS = 5 * 60

data class MeetingPart(val n: Int, val title: String, val minutes: Int)

/**
 * One week of the midweek meeting.
 * @param start Monday, "2026-11-02".
 * @param songs the opening, middle and closing song numbers.
 * @param livingFrom the first part of "Our Christian Life" (the middle song comes right before it).
 */
data class MeetingWeek(
    val start: String,
    val reading: String,
    val songs: List<Int>,
    val parts: List<MeetingPart>,
    val livingFrom: Int = 7,
)

data class MeetingProgram(val language: String, val updated: String, val weeks: List<MeetingWeek>)

/** A web request's answer: the HTTP status and the text (null when it failed). */
data class FetchResult(val status: Int, val text: String)

fun strings3ForCode(code: String): Strings3 = when (code) {
    "no" -> NorwegianStrings3
    "de" -> GermanStrings3
    else -> EnglishStrings3
}

/** Songs, comments and the week's parts, with the middle song before "Our Christian Life". */
fun midweekMeeting(week: MeetingWeek, s: Strings3, title: String): SpeechPlan {
    fun song(i: Int, prayer: Boolean): String {
        val n = week.songs.getOrNull(i)
        return when {
            n == null -> if (prayer) s.songPrayer else s.song
            prayer -> s.songPrayerN(n.toString())
            else -> s.songN(n.toString())
        }
    }
    val segs = mutableListOf(
        SpeechSegment("mw-song1", song(0, true), 5 * 60),
        SpeechSegment("mw-open", s.openingComments, 60),
    )
    var middleSong = false
    for (p in week.parts) {
        if (!middleSong && p.n >= week.livingFrom) {
            segs += SpeechSegment("mw-song2", song(1, false), 5 * 60)
            middleSong = true
        }
        segs += SpeechSegment("mw-p${p.n}", "${p.n}. ${p.title}", p.minutes.coerceAtLeast(0) * 60)
    }
    if (!middleSong) segs += SpeechSegment("mw-song2", song(1, false), 5 * 60)
    segs += SpeechSegment("mw-close", s.concludingComments, 3 * 60)
    segs += SpeechSegment("mw-song3", song(2, true), 5 * 60)
    return SpeechPlan(
        id = MIDWEEK_MEETING_ID,
        title = title,
        segments = segs,
        meeting = MeetingInfo(MeetingKind.MIDWEEK, MIDWEEK_BUFFER_SECONDS, week.start, week.reading),
    )
}

/** The weekend meeting: song and prayer, public talk 30, song, Watchtower Study 60, song and prayer. */
fun weekendMeeting(s: Strings3, title: String): SpeechPlan = SpeechPlan(
    id = WEEKEND_MEETING_ID,
    title = title,
    segments = listOf(
        SpeechSegment("we-song1", s.songPrayer, 5 * 60),
        SpeechSegment("we-talk", s.publicTalk, 30 * 60),
        SpeechSegment("we-song2", s.song, 5 * 60),
        SpeechSegment("we-wt", s.watchtowerStudy, 60 * 60),
        SpeechSegment("we-song3", s.songPrayer, 5 * 60),
    ),
    meeting = MeetingInfo(MeetingKind.WEEKEND),
)

// --- Sub-parts ---------------------------------------------------------------------------
// The chairman can split a part into sub-parts on the meeting screen (or bring in a saved
// speech), for example when he has a part himself. They're kept per week and part.

/** Where a part's sub-parts are kept: "2026-11-02/mw-p1" (midweek, by week) or "weekend/we-talk". */
fun subpartsKey(meeting: MeetingInfo?, segmentId: String): String {
    val week = if (meeting != null && meeting.kind == MeetingKind.MIDWEEK && meeting.week.isNotBlank()) meeting.week else "weekend"
    return "$week/$segmentId"
}

/**
 * The plan the timer runs: every part that has sub-parts is replaced by them, in order.
 * If the sub-parts are shorter than the part, the rest goes to the last one, so the meeting
 * keeps its length. If they're longer, they're used as they are.
 */
fun expandSubparts(plan: SpeechPlan, subpartsOf: (SpeechSegment) -> List<SpeechSegment>): SpeechPlan {
    val out = ArrayList<SpeechSegment>()
    for (seg in plan.segments) {
        val subs = subpartsOf(seg)
        if (subs.isEmpty()) {
            out += seg
            continue
        }
        val rest = (seg.targetSeconds - subs.sumOf { it.targetSeconds }).coerceAtLeast(0)
        subs.forEachIndexed { i, sub ->
            out += SpeechSegment(
                id = seg.id + "/" + sub.id,
                title = sub.title.trim().ifBlank { seg.title },
                targetSeconds = sub.targetSeconds + if (i == subs.lastIndex) rest else 0,
                group = seg.title,
            )
        }
    }
    return plan.copy(segments = out)
}

/** Drops sub-parts of midweek weeks that started before [oldestWeek] ("2026-09-07"). */
fun pruneSubparts(all: Map<String, List<SpeechSegment>>, oldestWeek: String): Map<String, List<SpeechSegment>> =
    all.filter { (key, list) ->
        val week = key.substringBefore('/')
        list.isNotEmpty() && (week == "weekend" || week >= oldestWeek)
    }

// --- Dates, without any date library (works on every platform) ------------------------

/** Days since 1970-01-01 for a date (Howard Hinnant's days_from_civil). */
fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
    val y = if (m <= 2) y0 - 1 else y0
    val era = Math.floorDiv(y, 400)
    val yoe = y - era * 400
    val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097L + doe - 719_468
}

/** "2026-11-02" back to year, month, day. */
fun parseIsoDate(iso: String): Triple<Int, Int, Int>? {
    val p = iso.trim().split("-")
    if (p.size != 3) return null
    val y = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    val d = p[2].toIntOrNull() ?: return null
    return Triple(y, m, d)
}

/** Monday of the week that [todayIso] is in, plus [weeks] weeks. */
fun mondayOf(todayIso: String, weeks: Int = 0): String {
    val (y, m, d) = parseIsoDate(todayIso) ?: return todayIso
    val days = daysFromCivil(y, m, d)
    // 1970-01-01 was a Thursday: (days + 3) mod 7 is 0 on Mondays
    val monday = days - Math.floorMod(days + 3, 7L) + 7L * weeks
    return isoDate(monday * 86_400_000L)
}

private val MONTHS = mapOf(
    "no" to listOf("januar", "februar", "mars", "april", "mai", "juni", "juli", "august", "september", "oktober", "november", "desember"),
    "de" to listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember"),
    "en" to listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"),
)

/** "2.–8. november", "November 2–8", "30. November–6. Dezember". */
fun weekLabel(startIso: String, lang: String): String {
    val (y, m, d) = parseIsoDate(startIso) ?: return startIso
    val end = isoDate((daysFromCivil(y, m, d) + 6) * 86_400_000L)
    val (_, m2, d2) = parseIsoDate(end) ?: return startIso
    val names = MONTHS[lang] ?: MONTHS.getValue("en")
    val a = names[m - 1]
    val b = names[m2 - 1]
    val same = m == m2
    return if (lang == "en") "$a $d–${if (same) "" else "$b "}$d2"
    else "$d.${if (same) "" else " $a"}–$d2. $b"
}
