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
 * - Small image: banda arancio da min a max, da sovrapporre al gauge del cuore
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
        val COLOR_BAND = Color.argb(200, 255, 140, 0) // arancio, leggermente trasparente
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
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = BAND
                strokeCap = Paint.Cap.BUTT
                color = COLOR_BAND
            }
            val r = INSET + BAND / 2f
            val oval = RectF(r, r, SIZE - r, SIZE - r)
            val a1 = angle(t.min.toDouble())
            val a2 = angle(t.max.toDouble())
            c.drawArc(oval, a1, maxOf(a2 - a1, 3f), false, p)  // almeno 3 gradi, per vederla
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
