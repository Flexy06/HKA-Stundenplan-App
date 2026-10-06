package de.flexy.stundenplan.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import de.flexy.stundenplan.data.Building
import de.flexy.stundenplan.data.GeoPoint
import de.flexy.stundenplan.data.RoomInfo

object Navigation {

    enum class Mode(val param: String) { BIKE("b"), WALK("w") }

    /** Navigation zum Raum (genaue Lage, falls bekannt – sonst Gebäudemitte). */
    fun intent(room: RoomInfo, mode: Mode): Intent = intent(room.target, mode)
    fun start(context: Context, room: RoomInfo, mode: Mode) = start(context, room.target, "HKA ${room.title}", mode)

    fun intent(building: Building, mode: Mode): Intent = intent(GeoPoint(building.lat, building.lon), mode)
    fun start(context: Context, building: Building, mode: Mode) =
        start(context, GeoPoint(building.lat, building.lon), "HKA ${building.code}", mode)

    /** Intent für Google-Maps-Navigation zum Punkt (Fallback: beliebige Karten-App). */
    fun intent(p: GeoPoint, mode: Mode): Intent {
        val uri = Uri.parse("google.navigation:q=${p.lat},${p.lon}&mode=${mode.param}")
        return Intent(Intent.ACTION_VIEW, uri)
            .setPackage("com.google.android.apps.maps")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun start(context: Context, p: GeoPoint, name: String, mode: Mode) {
        try {
            context.startActivity(intent(p, mode))
        } catch (_: ActivityNotFoundException) {
            val label = Uri.encode(name)
            val geo = Uri.parse("geo:${p.lat},${p.lon}?q=${p.lat},${p.lon}($label)")
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
