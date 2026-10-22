package it.emanuele.gmtbezel

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Schermata dell'app: chiede il permesso di posizione e mostra alba/tramonto calcolati. */
class MainActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (resources.displayMetrics.widthPixels * 0.14).toInt()

        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
        }
        val button = Button(this).apply {
            text = "Aggiorna posizione"
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

        if (LocationStore.hasPermission(this)) {
            refresh(force = true)
        } else {
            status.text = "Serve il permesso di posizione per calcolare alba e tramonto."
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        refresh(force = true)
    }

    private fun hhmm(h: Double): String {
        val totalMin = Math.round(h * 60).toInt() % (24 * 60)
        return String.format(Locale.ITALY, "%02d:%02d", totalMin / 60, totalMin % 60)
    }

    private fun refresh(force: Boolean) {
        status.text = "Cerco la posizione..."
        scope.launch {
            val place = LocationStore.get(this@MainActivity, force)
            val zone = ZoneId.systemDefault()
            val sun = SunCalc.compute(LocalDate.now(zone), place.lat, place.lon, zone)
            val where = if (place.fromDevice)
                String.format(Locale.ITALY, "Posizione: %.2f, %.2f", place.lat, place.lon)
            else "Posizione non disponibile: uso Roma"
            val times = when (sun) {
                is SunDay.Normal -> "Alba ${hhmm(sun.sunrise)}\nTramonto ${hhmm(sun.sunset)}"
                is SunDay.PolarDay -> "Il sole non tramonta oggi"
                is SunDay.PolarNight -> "Il sole non sorge oggi"
            }
            status.text = "$where\nFuso: ${zone.id}\n\n$times\n"

            // chiede al quadrante di ridisegnare subito la ghiera
            ComplicationDataSourceUpdateRequester.create(
                this@MainActivity,
                ComponentName(this@MainActivity, BezelComplicationService::class.java)
            ).requestUpdateAll()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
