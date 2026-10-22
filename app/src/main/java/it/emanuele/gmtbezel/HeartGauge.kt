package it.emanuele.gmtbezel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/** Disegno del gauge del battito: fascia rosso chiaro da min a max e pallino del valore attuale. */
object HeartGauge {

    // Posizione del gauge del cuore nel quadrante (pixel su 450x450, dal pannello Properties di WFS)
    const val FACE_X = 115
    const val FACE_Y = 79
    const val FACE_W = 101
    const val FACE_H = 101

    // Geometria del gauge del cuore nel quadrante (da regolare se serve)
    const val SIZE = 200          // il gauge si disegna a 200x200 e poi si riduce a FACE_W x FACE_H
    const val BAND = 23f          // spessore: copre l'anello grigio (11,5 px su 101 = 23 su 200)
    const val INSET = 2f          // l'anello grigio parte a 1 px dal bordo (2 su 200)
    const val GAUGE_MIN = 40.0    // bpm all'inizio del gauge
    const val GAUGE_MAX = 200.0   // bpm alla fine del gauge
    const val START = 225.0       // inizio del gauge, gradi da ore 12 in senso orario
    const val SWEEP = 270.0       // ampiezza del gauge

    // Banda min-max: rosso chiaro pieno
    val COLOR_BAND = Color.rgb(255, 110, 110)

    // Pallino del battito attuale
    const val DRAW_DOT = true
    const val DOT_RADIUS = BAND / 2f - 3f   // circa 8,5 px di diametro sul quadrante, dentro la fascia
    val COLOR_DOT = Color.WHITE
    val COLOR_DOT_BORDER = Color.rgb(220, 20, 20)

    /** bpm -> angolo Android (0 = ore 3, senso orario). */
    private fun angle(bpm: Double): Float {
        val f = ((bpm - GAUGE_MIN) / (GAUGE_MAX - GAUGE_MIN)).coerceIn(0.0, 1.0)
        return (START - 90.0 + f * SWEEP).toFloat()
    }

    fun bitmap(t: HeartStore.Today): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        run {
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
                dot.style = Paint.Style.STROKE; dot.strokeWidth = 3f; dot.color = COLOR_DOT_BORDER
                c.drawCircle(x, y, DOT_RADIUS, dot)
            }
        }
        return bmp
    }


    /** Disegna il gauge nella sua posizione sul quadrante, dentro una bitmap 450x450. */
    fun drawOnFace(c: Canvas, t: HeartStore.Today) {
        c.drawBitmap(bitmap(t), null,
            Rect(FACE_X, FACE_Y, FACE_X + FACE_W, FACE_Y + FACE_H),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }
}
