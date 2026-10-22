package it.emanuele.gmtbezel

import android.graphics.Bitmap
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
 * - Small image: solo il gauge (ora e' gia' disegnato dentro la ghiera, questo resta come alternativa)
 * - Short text:  "52-148"
 * - Long text:   "Min 52 · Max 148 bpm"
 */
class HeartComplicationService : SuspendingComplicationDataSourceService() {

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
                    SmallImage.Builder(
                        if (t == null) Icon.createWithBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
                        else Icon.createWithBitmap(HeartGauge.bitmap(t)),
                        SmallImageType.PHOTO).build(), desc
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
