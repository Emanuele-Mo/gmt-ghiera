package it.emanuele.gmtbezel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/**
 * Battito minimo e massimo di oggi.
 * - Small image: banda rosso chiaro da min a max, con il pallino del valore attuale, da sovrapporre al gauge del cuore
 * - Short text:  "52-148"
 * - Long text:   "Min 52 · Max 148 bpm"
 */
class HeartComplicationService : SuspendingComplicationDataSourceService() {

    companion object {
        // Geometria del gauge del cuore nel quadrante (da regolare se serve)
        const val SIZE = 200          // lato della bitmap; lo slot va messo esattamente sopra il gauge
        const val BAND = 16f          // spessore della banda
        const val INSET = 0f          // distanza della banda dal bordo dello slot
        const val GAUGE_MIN = 40.0    // bpm all'inizio del gauge
        const val GAUGE_MAX = 200.0   // bpm alla fine del gauge
        const val START = 225.0       // inizio del gauge, gradi da ore 12 in senso orario
        const val SWEEP = 270.0       // ampiezza del gauge

        // Banda min-max: rosso chiaro pieno
        val COLOR_BAND = Color.rgb(255, 110, 110)

        // Pallino del battito attuale
        const val DRAW_DOT = true
        const val DOT_RADIUS = BAND / 2f - 2f   // piu' piccolo della banda: resta dentro
        val COLOR_DOT = Color.WHITE
        val COLOR_DOT_BORDER = Color.rgb(220, 20, 20)
    }

    /** bpm -> angolo Android (0 = ore 3, senso orario). */
    private fun angle(bpm: Double): Float {
        val f = ((bpm - GAUGE_MIN) / (GAUGE_MAX - GAUGE_MIN)).coerceIn(0.0, 1.0)
        return (START - 90.0 + f * SWEEP).toFloat()
    }

    private fun drawBand(t: HeartStore.Today?): Icon {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        if (t != null) {
            val c = Canvas(bmp)
            val cx = SIZE / 2f
            val r = INSET + BAND / 2f
            val oval = RectF(r, r, SIZE - r, SIZE - r)
            val a1 = angle(t.min.toDouble())
            val sweep = maxOf(angle(t.max.toDouble()) - a1, 3f)   // almeno 3 gradi, per vederla

            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = BAND
                strokeCap = Paint.Cap.BUTT
                color = COLOR_BAND
            }
            c.drawArc(oval, a1, sweep, false, p)

            // pallino del valore attuale, sull'asse della banda e sempre dentro la fascia rossa
            if (DRAW_DOT && t.last > 0) {
                val rr = cx - r
                // margine in gradi pari al raggio del pallino: non tocca mai le estremita' della fascia
                val margin = Math.toDegrees(((DOT_RADIUS + 1f) / rr).toDouble()).toFloat()
                val lo = a1 + margin
                val hi = a1 + sweep - margin
                val aDeg = if (hi <= lo) a1 + sweep / 2f
                           else angle(t.last.toDouble()).coerceIn(lo, hi)
                val a = Math.toRadians(aDeg.toDouble())
                val x = cx + (rr * Math.cos(a)).toFloat()
                val y = cx + (rr * Math.sin(a)).toFloat()
                val dot = Paint(Paint.ANTI_ALIAS_FLAG)
                dot.style = Paint.Style.FILL; dot.color = COLOR_DOT
                c.drawCircle(x, y, DOT_RADIUS, dot)
                dot.style = Paint.Style.STROKE; dot.strokeWidth = 2f; dot.color = COLOR_DOT_BORDER
                c.drawCircle(x, y, DOT_RADIUS, dot)
            }
        }
        return Icon.createWithBitmap(bmp)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, HeartStore.Today(52, 148, 70))

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        HeartStore.register(this)   // rinnova il monitoraggio (es. dopo un riavvio)
        return build(request.complicationType, HeartStore.today(this))
    }

    private fun build(type: ComplicationType, t: HeartStore.Today?): ComplicationData? {
        val short = if (t == null) "--" else "${t.min}-${t.max}"
        val long = if (t == null) "Battito: nessun dato oggi" else "Min ${t.min} · Max ${t.max} bpm"
        val desc = PlainComplicationText.Builder(long).build()
        return when (type) {
            ComplicationType.SMALL_IMAGE ->
                SmallImageComplicationData.Builder(
                    SmallImage.Builder(drawBand(t), SmallImageType.PHOTO).build(), desc
                ).build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(PlainComplicationText.Builder(short).build(), desc)
                    .setTitle(PlainComplicationText.Builder("bpm").build())
                    .build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(PlainComplicationText.Builder(long).build(), desc)
                    .build()
            else -> null
        }
    }
}
