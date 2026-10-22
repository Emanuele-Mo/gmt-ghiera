package it.emanuele.gmtbezel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import java.time.LocalDate
import java.time.ZoneId

/**
 * Ghiera 24h giorno/notte con alba e tramonto reali.
 * Scala della ghiera: 24 in alto, 6 a destra, 12 in basso, 18 a sinistra (15 gradi per ora).
 */
class BezelComplicationService : SuspendingComplicationDataSourceService() {

    companion object {
        const val TAG = "GmtGhiera"
        const val SIZE = 450                     // lato della bitmap in pixel
        const val RING = 40f                     // spessore dell'anello
        val COLOR_NIGHT = Color.rgb(20, 60, 200) // blu notte
        val COLOR_DAY = Color.rgb(200, 30, 30)   // rosso giorno

        const val DRAW_RIFLESSO = false          // riflesso semitrasparente sopra i colori
        const val DRAW_TACCHE = true             // immagine originale di numeri e divisioni (tacche_numeri.png)
        const val DRAW_NUMBERS = false           // numeri disegnati dall'app (alternativa)
        const val NUMBER_SIZE = 32f              // altezza del testo dei numeri
        val COLOR_NUMBERS = Color.WHITE
        val NUMBERS = listOf(2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24)
    }

    private val riflesso: Bitmap? by lazy {
        runCatching { BitmapFactory.decodeResource(resources, R.drawable.riflesso_ghiera) }.getOrNull()
    }

    private val lunaFoto: Bitmap? by lazy {
        runCatching { BitmapFactory.decodeResource(resources, R.drawable.luna) }.getOrNull()
    }

    private val tacche: Bitmap? by lazy {
        runCatching { BitmapFactory.decodeResource(resources, R.drawable.tacche_numeri) }.getOrNull()
    }

    /** Numeri sull'anello, orientati come una ghiera (base verso il centro). */
    private fun drawNumbers(c: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_NUMBERS
            textSize = NUMBER_SIZE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val cx = SIZE / 2f
        val radius = SIZE / 2f - RING / 2f                    // centro dell'anello
        val baseline = cx - radius - (p.ascent() + p.descent()) / 2f
        for (h in NUMBERS) {
            c.save()
            c.rotate((h % 24) * 15f, cx, cx)
            c.drawText(h.toString(), cx, baseline, p)
            c.restore()
        }
    }

    /** Ora locale (0-24) -> angolo Android (0 = ore 3 del quadrante, senso orario). */
    private fun angle(hour: Double): Float = (hour * 15.0 - 90.0).toFloat()

    private fun draw(sun: SunDay, heart: HeartStore.Today?, moon: MoonCalc.State?): Icon {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = RING
            strokeCap = Paint.Cap.BUTT
        }
        val inset = RING / 2f
        val oval = RectF(inset, inset, SIZE - inset, SIZE - inset)

        when (sun) {
            is SunDay.PolarDay -> {
                p.color = COLOR_DAY; c.drawArc(oval, 0f, 360f, false, p)
            }
            is SunDay.PolarNight -> {
                p.color = COLOR_NIGHT; c.drawArc(oval, 0f, 360f, false, p)
            }
            is SunDay.Normal -> {
                var dayHours = sun.sunset - sun.sunrise
                if (dayHours < 0) dayHours += 24.0
                val daySweep = (dayHours * 15.0).toFloat()
                // prima tutto blu, poi l'arco rosso del giorno sopra
                p.color = COLOR_NIGHT
                c.drawArc(oval, 0f, 360f, false, p)
                p.color = COLOR_DAY
                c.drawArc(oval, angle(sun.sunrise), daySweep, false, p)
            }
        }
        if (DRAW_RIFLESSO) riflesso?.let {
            c.drawBitmap(it, null, Rect(0, 0, SIZE, SIZE), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        if (DRAW_TACCHE) tacche?.let {
            c.drawBitmap(it, null, Rect(0, 0, SIZE, SIZE), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        if (DRAW_NUMBERS) drawNumbers(c)
        // ogni parte e' protetta: se una va in errore, il resto dell'immagine compare comunque
        heart?.let { h -> safe("cuore") { HeartGauge.drawOnFace(c, h) } }
        val foto = lunaFoto
        if (moon != null && foto != null) safe("luna") { MoonDraw.draw(c, foto, moon) }
        return Icon.createWithBitmap(bmp)
    }

    private fun sunToday(place: LocationStore.Place): SunDay {
        val zone = ZoneId.systemDefault()
        return SunCalc.compute(LocalDate.now(zone), place.lat, place.lon, zone)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val sun = SunDay.Normal(7.5, 18.25)
        val icon = runCatching {
            draw(sun, HeartStore.Today(52, 148, 70), MoonCalc.compute(System.currentTimeMillis(), 41.9, 12.5))
        }.getOrElse { Log.e(TAG, "anteprima", it); draw(sun, null, null) }
        return build(type, icon)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        // Risposta immediata con la posizione salvata: il quadrante non deve mai aspettare il GPS
        val place = LocationStore.cached(this)
        LocationStore.refreshInBackground(this)        // se e' vecchia, la aggiorna dopo e ridisegna
        safe("battito") { HeartStore.register(this) }  // rinnova il monitoraggio (es. dopo un riavvio)

        val sun = runCatching { sunToday(place) }.getOrElse { Log.w(TAG, "sole", it); SunDay.Normal(7.0, 18.0) }
        val heart = runCatching { HeartStore.today(this) }.getOrNull()
        val moon = runCatching { MoonCalc.compute(System.currentTimeMillis(), place.lat, place.lon) }
            .onFailure { Log.w(TAG, "calcolo luna", it) }.getOrNull()
        val icon = runCatching { draw(sun, heart, moon) }.getOrElse {
            Log.e(TAG, "disegno completo fallito, riprovo senza extra", it)
            draw(sun, null, null)
        }
        return build(request.complicationType, icon)
    }

    private inline fun safe(what: String, block: () -> Unit) {
        try { block() } catch (e: Throwable) { Log.w(TAG, "errore nel disegno: $what", e) }
    }

    private fun build(type: ComplicationType, icon: Icon): ComplicationData? {
        val desc = PlainComplicationText.Builder("Ghiera GMT").build()
        return when (type) {
            ComplicationType.PHOTO_IMAGE ->
                PhotoImageComplicationData.Builder(icon, desc).build()
            ComplicationType.SMALL_IMAGE ->
                SmallImageComplicationData.Builder(
                    SmallImage.Builder(icon, SmallImageType.PHOTO).build(), desc
                ).build()
            else -> null
        }
    }
}
