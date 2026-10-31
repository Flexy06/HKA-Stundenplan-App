package de.flexy.stundenplan.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Einheitliche haptische Rückmeldung für die App. Android blendet sie selbst aus,
 * wenn der Nutzer "Touch-Feedback" in den Systemeinstellungen abgeschaltet hat.
 */
class Haptics(private val feedback: HapticFeedback) {
    /** Leichtes Ticken: Tippen auf Karten/Tage/Buttons, Seitenwechsel. */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** Schalter und Chips. */
    fun toggle(on: Boolean) =
        feedback.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    /** Pull-to-Refresh wurde ausgelöst. */
    fun threshold() = feedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

    /** Aktion abgeschlossen (z.B. Aktualisieren fertig). */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}
