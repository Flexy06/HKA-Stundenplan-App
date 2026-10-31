package de.flexy.stundenplan.data

import java.time.LocalDateTime

/**
 * Ruhezeiten für "Handy stumm in Vorlesungen": zusammenhängende Blöcke aus Terminen
 * und der jeweils nächste Wechsel (stumm schalten / wieder laut).
 */
object QuietTimes {
    data class Block(val start: LocalDateTime, val end: LocalDateTime)

    /** [at]: Zeitpunkt des Wechsels, [silence]: true = ab jetzt stumm, false = wieder normal. */
    data class Transition(val at: LocalDateTime, val silence: Boolean)

    /**
     * Stattfindende Termine zu Blöcken zusammenfassen. Lücken bis [mergeGapMinutes] (z.B. Raumwechsel)
     * zählen mit, damit das Handy zwischen zwei Vorlesungen nicht kurz laut wird.
     */
    fun blocks(lectures: List<Lecture>, mergeGapMinutes: Long = 10): List<Block> {
        val out = ArrayList<Block>()
        for (l in lectures.filter { !it.cancelled }.sortedBy { it.start }) {
            val last = out.lastOrNull()
            if (last != null && !l.start.isAfter(last.end.plusMinutes(mergeGapMinutes))) {
                if (l.end.isAfter(last.end)) out[out.lastIndex] = last.copy(end = l.end)
            } else {
                out += Block(l.start, l.end)
            }
        }
        return out
    }

    fun isQuiet(blocks: List<Block>, now: LocalDateTime): Boolean =
        blocks.any { !now.isBefore(it.start) && now.isBefore(it.end) }

    /** Nächster Wechsel: mitten im Block → Ende des Blocks, sonst Beginn des nächsten Blocks. */
    fun next(blocks: List<Block>, now: LocalDateTime): Transition? {
        blocks.firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }?.let { return Transition(it.end, false) }
        return blocks.firstOrNull { it.start.isAfter(now) }?.let { Transition(it.start, true) }
    }
}
