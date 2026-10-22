package it.emanuele.gmtbezel

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Disegna la Luna con la foto reale, la fase continua e l'orientamento
 * visto dall'osservatore (alto dello schermo = zenit).
 */
object MoonDraw {

    // Posizione della Luna nel quadrante (pixel su 450x450, dal pannello Properties di WFS)
    const val FACE_X = 235
    const val FACE_Y = 269
    const val FACE_D = 101                // diametro

    const val NIGHT_ALPHA = 240           // 0..255: oscurita' della parte in ombra (255 = nero pieno)
    const val TERMINATOR_SOFT = 0.03f     // morbidezza del terminatore, in frazione del diametro

    fun draw(c: Canvas, photo: Bitmap, st: MoonCalc.State) {
        val d = FACE_D.toFloat()
        val r = d / 2f
        val cx = FACE_X + r
        val cy = FACE_Y + r
        val disk = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }

        c.save()
        c.clipPath(disk)

        // 1) foto, ruotata in modo che il nord lunare punti dove lo vedi in cielo
        c.save()
        c.rotate(-st.northDeg, cx, cy)   // Canvas ruota in senso orario, gli angoli sono antiorari
        c.drawBitmap(photo, null, RectF(cx - r, cy - r, cx + r, cy + r),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        c.restore()

        // 2) ombra: in un sistema locale con +x verso il lembo illuminato
        val a = Math.toRadians(st.brightLimbDeg.toDouble())
        val dirX = -sin(a)               // direzione del lembo illuminato sullo schermo
        val dirY = -cos(a)
        val rot = Math.toDegrees(atan2(dirY, dirX)).toFloat()

        val cosI = cos(st.phaseAngle).toFloat()
        val shadow = Path()
        val steps = 64
        // bordo del terminatore, dall'alto al basso
        for (s in 0..steps) {
            val y = -r + 2f * r * s / steps
            val half = sqrt((r * r - y * y).coerceAtLeast(0f))
            val x = -half * cosI
            if (s == 0) shadow.moveTo(x, y) else shadow.lineTo(x, y)
        }
        // bordo esterno della parte in ombra, dal basso all'alto (allargato per coprire la sfumatura)
        val pad = d * TERMINATOR_SOFT * 2f
        for (s in steps downTo 0) {
            val y = -r + 2f * r * s / steps
            val half = sqrt((r * r - y * y).coerceAtLeast(0f))
            shadow.lineTo(-half - pad, y)
        }
        shadow.close()

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(NIGHT_ALPHA, 0, 0, 0)
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(d * TERMINATOR_SOFT, BlurMaskFilter.Blur.NORMAL)
        }
        c.save()
        c.translate(cx, cy)
        c.rotate(rot)
        c.drawPath(shadow, p)
        c.restore()

        c.restore()
    }
}
