package it.emanuele.gmtbezel

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Schermata dell'app: chiede i permessi e mostra alba/tramonto, luna e battito.
 * Mentre e' aperta misura anche il battito dal vivo, cosi' il gauge ha subito un dato.
 */
class MainActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var status: TextView
    private var liveBpm: Int = 0
    private var measuring = false
    private var lastPush = 0L

    private val bgPermission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.BODY_SENSORS_BACKGROUND else null

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (resources.displayMetrics.widthPixels * 0.14).toInt()

        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
        }
        val button = Button(this).apply {
            text = "Aggiorna"
            setOnClickListener { refresh(force = true) }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad)
            addView(status)
            addView(button)
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.BLACK); addView(col) })
        askPermissions()
    }

    /** Prima posizione e sensori; poi, a parte (come richiede Android), i sensori in background. */
    private fun askPermissions() {
        val base = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.BODY_SENSORS)
            .filter { !granted(it) }
        when {
            base.isNotEmpty() -> {
                status.text = "Servono i permessi di posizione (alba, tramonto, luna) e sensori (battito)."
                requestPermissions(base.toTypedArray(), 1)
            }
            bgPermission != null && !granted(bgPermission) -> {
                status.text = "Per il battito a quadrante chiuso scegli \"Consenti sempre\"."
                requestPermissions(arrayOf(bgPermission), 2)
            }
            else -> onReady()
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 1 && granted(Manifest.permission.BODY_SENSORS) &&
            bgPermission != null && !granted(bgPermission)) {
            requestPermissions(arrayOf(bgPermission), 2)
        } else {
            onReady()
        }
    }

    private fun onReady() {
        HeartStore.register(this)
        startLiveHeart()
        refresh(force = true)
    }

    override fun onResume() {
        super.onResume()
        if (granted(Manifest.permission.BODY_SENSORS)) startLiveHeart()
    }

    override fun onPause() {
        stopLiveHeart()
        super.onPause()
    }

    // --- misura dal vivo mentre l'app e' aperta ---
    private val liveCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {}
        override fun onDataReceived(data: DataPointContainer) {
            val boot = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
            var changed = false
            for (dp in data.getData(DataType.HEART_RATE_BPM)) {
                if (dp.value > 0) liveBpm = Math.round(dp.value).toInt()
                if (HeartStore.add(this@MainActivity, dp.value, dp.getTimeInstant(boot))) changed = true
            }
            // ridisegna il quadrante al massimo una volta al minuto
            val now = SystemClock.elapsedRealtime()
            if (changed && now - lastPush > 60_000L) {
                lastPush = now
                HeartStore.requestComplicationUpdate(this@MainActivity)
            }
            scope.launch { refresh(force = false) }
        }
    }

    private fun startLiveHeart() {
        if (measuring || !granted(Manifest.permission.BODY_SENSORS)) return
        try {
            HealthServices.getClient(this).measureClient
                .registerMeasureCallback(DataType.HEART_RATE_BPM, liveCallback)
            measuring = true
        } catch (e: Exception) {
            android.util.Log.w("GmtGhiera", "misura dal vivo non disponibile", e)
        }
    }

    private fun stopLiveHeart() {
        if (!measuring) return
        measuring = false
        try {
            HealthServices.getClient(this).measureClient
                .unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, liveCallback)
        } catch (_: Exception) { }
    }

    private fun hhmm(h: Double): String {
        val totalMin = Math.round(h * 60).toInt() % (24 * 60)
        return String.format(Locale.ITALY, "%02d:%02d", totalMin / 60, totalMin % 60)
    }

    private fun refresh(force: Boolean) {
        scope.launch {
            val place = if (force) LocationStore.get(this@MainActivity, true) else LocationStore.cached(this@MainActivity)
            val zone = ZoneId.systemDefault()
            val sun = SunCalc.compute(LocalDate.now(zone), place.lat, place.lon, zone)
            val where = if (place.fromDevice)
                String.format(Locale.ITALY, "Posizione: %.2f, %.2f", place.lat, place.lon)
            else "Posizione non disponibile: uso Roma"
            val times = when (sun) {
                is SunDay.Normal -> "Alba ${hhmm(sun.sunrise)}  Tramonto ${hhmm(sun.sunset)}"
                is SunDay.PolarDay -> "Il sole non tramonta oggi"
                is SunDay.PolarNight -> "Il sole non sorge oggi"
            }
            val m = MoonCalc.compute(System.currentTimeMillis(), place.lat, place.lon)
            val moonTxt = String.format(Locale.ITALY, "Luna %s, %d%%",
                if (m.waxing) "crescente" else "calante", Math.round(m.illuminated * 100))

            val perm = when {
                !granted(Manifest.permission.BODY_SENSORS) -> "Permesso sensori: NO"
                bgPermission != null && !granted(bgPermission) -> "Sensori in background: NO\n(Impostazioni > App > GMT Ghiera > Autorizzazioni)"
                else -> "Permessi battito: OK"
            }
            val today = HeartStore.today(this@MainActivity)
            val heart = today?.let { "Battito oggi: min ${it.min}, max ${it.max}" } ?: "Battito: nessun dato oggi"
            val live = if (liveBpm > 0) "Ora: $liveBpm bpm" else if (measuring) "Misuro..." else ""

            status.text = "$where\n$times\n$moonTxt\n\n$heart\n$live\n$perm\n"

            if (force) ComplicationDataSourceUpdateRequester.create(
                this@MainActivity,
                ComponentName(this@MainActivity, BezelComplicationService::class.java)
            ).requestUpdateAll()
        }
    }

    override fun onDestroy() {
        stopLiveHeart()
        scope.cancel()
        super.onDestroy()
    }
}
