package it.emanuele.gmtbezel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Posizione approssimativa dell'orologio, salvata in memoria.
 * Se manca il permesso o la posizione, usa Roma.
 */
object LocationStore {

    private const val PREFS = "gmt_location"
    private const val ROMA_LAT = 41.9028
    private const val ROMA_LON = 12.4964
    private const val MAX_AGE_MS = 3L * 60 * 60 * 1000   // rilegge al massimo ogni 3 ore

    data class Place(val lat: Double, val lon: Double, val fromDevice: Boolean)

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun cached(ctx: Context): Place {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (p.contains("lat")) {
            Place(p.getFloat("lat", 0f).toDouble(), p.getFloat("lon", 0f).toDouble(), true)
        } else {
            Place(ROMA_LAT, ROMA_LON, false)
        }
    }

    /** Restituisce la posizione, aggiornandola solo se quella salvata e' vecchia. */
    suspend fun get(ctx: Context, force: Boolean = false): Place {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val age = System.currentTimeMillis() - prefs.getLong("time", 0L)
        if (!force && age < MAX_AGE_MS) return cached(ctx)
        if (!hasPermission(ctx)) return cached(ctx)

        val loc = fetch(ctx) ?: return cached(ctx)
        prefs.edit()
            .putFloat("lat", loc.latitude.toFloat())
            .putFloat("lon", loc.longitude.toFloat())
            .putLong("time", System.currentTimeMillis())
            .apply()
        return Place(loc.latitude, loc.longitude, true)
    }

    @Suppress("MissingPermission")
    private suspend fun fetch(ctx: Context): Location? {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val enabled = lm.getProviders(true)
        val order = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        val provider = order.firstOrNull { it in enabled } ?: return null

        // Prima prova la posizione nota piu' recente (gratis per la batteria)
        val last = order.filter { it in enabled }
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < MAX_AGE_MS) return last

        // Altrimenti chiede una posizione attuale, con un limite di 30 secondi
        return withTimeoutOrNull(30_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val cancel = CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                try {
                    lm.getCurrentLocation(provider, cancel, ctx.mainExecutor) { l ->
                        if (cont.isActive) cont.resume(l)
                    }
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        } ?: last
    }
}
