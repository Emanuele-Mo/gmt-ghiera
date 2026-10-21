package it.emanuele.gmtbezel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

/** TEST: disegna una ghiera di prova (meta' blu, meta' rossa) 450x450. */
class BezelComplicationService : SuspendingComplicationDataSourceService() {

    private fun draw(): Icon {
        val n = 450
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 40f
        }
        val r = n / 2f - 20f
        val oval = android.graphics.RectF(20f, 20f, n - 20f, n - 20f)
        p.color = Color.rgb(20, 60, 200)
        c.drawArc(oval, 135f, 180f, false, p)   // meta' superiore (notte)
        p.color = Color.rgb(200, 30, 30)
        c.drawArc(oval, 315f, 180f, false, p)   // meta' inferiore (giorno)
        return Icon.createWithBitmap(bmp)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType)

    private fun build(type: ComplicationType): ComplicationData? {
        val desc = PlainComplicationText.Builder("Ghiera GMT").build()
        return when (type) {
            ComplicationType.PHOTO_IMAGE ->
                PhotoImageComplicationData.Builder(draw(), desc).build()
            ComplicationType.SMALL_IMAGE ->
                SmallImageComplicationData.Builder(
                    SmallImage.Builder(draw(), SmallImageType.PHOTO).build(), desc
                ).build()
            else -> null
        }
    }
}
