package de.flexy.stundenplan.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SharedLogicTest {

    private fun lec(title: String, start: String, end: String, room: String = "N-213", cancelled: Boolean = false) =
        Lecture(
            id = "$title$start", title = title, code = "", location = room, cancelled = cancelled,
            start = LocalDateTime.parse("2026-11-02T$start"), end = LocalDateTime.parse("2026-11-02T$end"),
        )

    @Test fun conflictsFindOverlaps() {
        val a = lec("A", "08:00", "09:30")
        val b = lec("B", "09:00", "10:30")
        val c = lec("C", "10:30", "12:00") // schließt direkt an B an → kein Konflikt
        assertEquals(setOf(a, b), ScheduleInsights.conflicts(listOf(c, b, a)))
    }

    @Test fun conflictsCatchLongEnclosingLecture() {
        val long = lec("Lang", "08:00", "13:00")
        val x = lec("X", "09:00", "10:00")
        val y = lec("Y", "11:00", "12:00") // überlappt nur mit "Lang", nicht mit X
        assertEquals(setOf(long, x, y), ScheduleInsights.conflicts(listOf(long, x, y)))
    }

    @Test fun conflictsIgnoreCancelled() {
        val a = lec("A", "08:00", "09:30")
        val b = lec("B", "09:00", "10:30", cancelled = true)
        assertTrue(ScheduleInsights.conflicts(listOf(a, b)).isEmpty())
    }

    @Test fun busyMinutesCountsOverlapOnce() {
        val list = listOf(lec("A", "08:00", "09:30"), lec("B", "09:00", "10:30"), lec("C", "12:00", "13:00"))
        assertEquals(150L + 60L, ScheduleInsights.busyMinutes(list))
        assertEquals(0L, ScheduleInsights.busyMinutes(emptyList()))
    }

    @Test fun icsExportRoundTripsAndSkipsCancelled() {
        val list = listOf(
            lec("Mathe; Höhere, Teil 1", "08:00", "09:30", "N-213"),
            lec("Fällt aus", "10:00", "11:30", cancelled = true),
        )
        val ics = IcsExport.build(list, "ELTB.1.A")
        assertTrue(ics.startsWith("BEGIN:VCALENDAR"))
        assertTrue(ics.lines().all { it.toByteArray().size <= 76 })
        val back = IcsParser.parse(ics)
        assertEquals(1, back.size)
        assertEquals("Mathe; Höhere, Teil 1", back[0].title)
        assertEquals(list[0].start, back[0].start)
        assertEquals("N-213", back[0].location)
    }

    @Test fun parsesRooms() {
        val r = Campus.parseRoom("N-213")
        assertNotNull(r)
        assertEquals("2. OG", r!!.floor)
        assertEquals("LI", Campus.parseRoom("Hörsaal HE")!!.building.code)
        assertNull(Campus.parseRoom("Exkursion"))
        assertEquals(2, Campus.parseRooms("N-213, E-101").size)
    }

    @Test fun filterHonoursRhythmAndSkips() {
        val a = lec("Labor", "08:00", "09:30")
        val even = LectureFilter.isEvenWeek(a.date)
        val f = LectureFilter(rhythms = mapOf("Labor" to LectureFilter.biweeklyFor(a, with = true)))
        assertTrue(f.shows(a))
        assertFalse(f.shows(a.copy(start = a.start.plusWeeks(1), end = a.end.plusWeeks(1))))
        assertFalse(LectureFilter(skipped = setOf(LectureFilter.key(a))).shows(a))
        assertEquals(even, LectureFilter.isEvenWeek(a.date))
    }

    @Test fun dedupeKeepsLongestTitle() {
        val short = lec("Mathematik", "08:00", "09:30")
        val long = lec("Mathematik/Höhere Mathematik 1", "08:00", "09:30")
        assertEquals(listOf(long), TimetableBuilder.dedupe(listOf(short, long)))
    }
}
