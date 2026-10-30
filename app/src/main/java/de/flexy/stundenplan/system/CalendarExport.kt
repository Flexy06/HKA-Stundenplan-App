package de.flexy.stundenplan.system

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import de.flexy.stundenplan.data.IcsExport
import de.flexy.stundenplan.data.Lecture
import java.io.File

/** Teilt den (gefilterten) Stundenplan als .ics-Datei, z.B. für Google Kalender oder Outlook. */
object CalendarExport {
    fun share(context: Context, lectures: List<Lecture>, semester: String) {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, "Stundenplan-${semester.replace(Regex("[^A-Za-z0-9._-]"), "_")}.ics")
        file.writeText(IcsExport.build(lectures, semester), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Stundenplan exportieren").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
