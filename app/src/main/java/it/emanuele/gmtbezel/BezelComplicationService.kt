package it.emanuele.gmtbezel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Icon
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
        const val SIZE = 450                     // lato della bitmap in pixel
        const val RING = 40f                     // spessore dell'anello
        val COLOR_NIGHT = Color.rgb(20, 60, 200) // blu notte
        val COLOR_DAY = Color.rgb(200, 30, 30)   // rosso giorno
    }

    /** Ora locale (0-24) -> angolo Android (0 = ore 3 del quadrante, senso orario). */
    private fun angle(hour: Double): Float = (hour * 15.0 - 90.0).toFloat()

    private fun draw(sun: SunDay): Icon {
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
        return Icon.createWithBitmap(bmp)
    }

    private fun sunToday(place: LocationStore.Place): SunDay {
        val zone = ZoneId.systemDefault()
        return SunCalc.compute(LocalDate.now(zone), place.lat, place.lon, zone)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, draw(SunDay.Normal(7.5, 18.25)))

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val place = LocationStore.get(this)
        return build(request.complicationType, draw(sunToday(place)))
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
