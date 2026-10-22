package it.emanuele.gmtbezel

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Minimo e massimo del battito di oggi, calcolati dai campioni di Health Services.
 * Si azzerano al cambio di giorno (mezzanotte locale).
 */
object HeartStore {

    private const val PREFS = "gmt_heart"
    private const val TAG = "GmtHeart"

    data class Today(val min: Int, val max: Int, val last: Int)

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.BODY_SENSORS) ==
            PackageManager.PERMISSION_GRANTED

    /** Registra (o rinnova) il monitoraggio passivo del battito. Chiamarlo piu' volte non fa danni. */
    fun register(ctx: Context) {
        if (!hasPermission(ctx)) return
        try {
            val config = PassiveListenerConfig.builder()
                .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                .build()
            val future = HealthServices.getClient(ctx.applicationContext)
                .passiveMonitoringClient
                .setPassiveListenerServiceAsync(HeartListenerService::class.java, config)
            future.addListener({
                try { future.get(); Log.i(TAG, "monitoraggio battito attivo") }
                catch (e: Exception) { Log.w(TAG, "registrazione fallita", e) }
            }, ctx.mainExecutor)
        } catch (e: Exception) {
            Log.w(TAG, "Health Services non disponibile", e)
        }
    }

    /** Aggiunge un campione; restituisce true se minimo, massimo o valore attuale sono cambiati. */
    fun add(ctx: Context, bpm: Double, time: Instant): Boolean {
        if (bpm < 25 || bpm > 250) return false          // scarta letture assurde
        val zone = ZoneId.systemDefault()
        val sampleDay = time.atZone(zone).toLocalDate().toEpochDay()
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val day = p.getLong("day", -1)
        if (sampleDay < day) return false                // campione di ieri arrivato in ritardo

        val v = Math.round(bpm).toInt()
        val newDay = sampleDay > day
        val min = if (newDay) v else minOf(p.getInt("min", v), v)
        val max = if (newDay) v else maxOf(p.getInt("max", v), v)
        // aggiorna anche quando cambia il valore attuale, per spostare il pallino
        val changed = newDay || min != p.getInt("min", -1) || max != p.getInt("max", -1) ||
            v != p.getInt("last", -1)
        p.edit()
            .putLong("day", sampleDay)
            .putInt("min", min)
            .putInt("max", max)
            .putInt("last", v)
            .apply()
        return changed
    }

    fun today(ctx: Context): Today? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val todayEpoch = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        if (p.getLong("day", -1) != todayEpoch) return null
        return Today(p.getInt("min", 0), p.getInt("max", 0), p.getInt("last", 0))
    }

    /** Ridisegna sia la ghiera (che contiene il gauge) sia la complicazione separata del battito. */
    fun requestComplicationUpdate(ctx: Context) {
        for (cls in listOf(BezelComplicationService::class.java, HeartComplicationService::class.java)) {
            ComplicationDataSourceUpdateRequester.create(ctx, ComponentName(ctx, cls)).requestUpdateAll()
        }
    }
}
