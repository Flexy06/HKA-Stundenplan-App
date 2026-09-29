package de.flexy.stundenplan.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import de.flexy.stundenplan.data.Building

object Navigation {

    enum class Mode(val param: String) { BIKE("b"), WALK("w") }

    /** Intent für Google-Maps-Navigation zum Gebäude (Fallback: beliebige Karten-App). */
    fun intent(building: Building, mode: Mode): Intent {
        val uri = Uri.parse("google.navigation:q=${building.lat},${building.lon}&mode=${mode.param}")
        return Intent(Intent.ACTION_VIEW, uri)
            .setPackage("com.google.android.apps.maps")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun start(context: Context, building: Building, mode: Mode) {
        try {
            context.startActivity(intent(building, mode))
        } catch (_: ActivityNotFoundException) {
            val label = Uri.encode("HKA ${building.code}")
            val geo = Uri.parse("geo:${building.lat},${building.lon}?q=${building.lat},${building.lon}($label)")
            openSafely(context, Intent(Intent.ACTION_VIEW, geo))
        }
    }

    fun openUrl(context: Context, url: String) =
        openSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun openSafely(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }
}
