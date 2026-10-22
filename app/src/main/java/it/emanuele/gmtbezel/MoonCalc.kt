package it.emanuele.gmtbezel

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Posizione e aspetto della Luna per un osservatore (formule ridotte di Meeus, cap. 47).
 * Precisione: fase entro ~0,5%, angoli entro ~1 grado, piu' che sufficiente per il quadrante.
 */
object MoonCalc {

    data class State(
        val illuminated: Double,     // frazione illuminata 0..1
        val phaseAngle: Double,      // angolo di fase in radianti (0 = piena, PI = nuova)
        val brightLimbDeg: Float,    // direzione del lembo illuminato, gradi da "alto" in senso antiorario
        val northDeg: Float,         // direzione del nord lunare (celeste), gradi da "alto" in senso antiorario
        val waxing: Boolean,         // crescente
        val altitudeDeg: Double      // altezza sull'orizzonte (negativa = sotto l'orizzonte)
    )

    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun norm(x: Double) = ((x % 360.0) + 360.0) % 360.0

    fun compute(epochMs: Long, latDeg: Double, lonDeg: Double): State {
        val d = epochMs / 86_400_000.0 + 2440587.5 - 2451545.0   // giorni da J2000
        val t = d / 36525.0

        // Luna
        val lp = norm(218.3164477 + 481267.88123421 * t)
        val dm = norm(297.8501921 + 445267.1114034 * t)
        val m = norm(357.5291092 + 35999.0502909 * t)
        val mp = norm(134.9633964 + 477198.8675055 * t)
        val f = norm(93.2720950 + 483202.0175233 * t)
        fun s(a: Double) = sin(rad(a))
        fun c(a: Double) = cos(rad(a))

        val lam = lp + 6.288774 * s(mp) + 1.274027 * s(2 * dm - mp) + 0.658314 * s(2 * dm) +
            0.213618 * s(2 * mp) - 0.185116 * s(m) - 0.114332 * s(2 * f) +
            0.058793 * s(2 * dm - 2 * mp) + 0.057066 * s(2 * dm - m - mp) + 0.053322 * s(2 * dm + mp) +
            0.045758 * s(2 * dm - m) - 0.040923 * s(m - mp) - 0.034720 * s(dm) - 0.030383 * s(m + mp)
        val bet = 5.128122 * s(f) + 0.280602 * s(mp + f) + 0.277693 * s(mp - f) +
            0.173237 * s(2 * dm - f) + 0.055413 * s(2 * dm - mp + f) + 0.046271 * s(2 * dm - mp - f)
        val dist = 385000.56 - 20905.355 * c(mp) - 3699.111 * c(2 * dm - mp) -
            2955.968 * c(2 * dm) - 569.925 * c(2 * mp)

        // Sole
        val l0 = 280.46646 + 36000.76983 * t
        val cs = (1.914602 - 0.004817 * t) * s(m) + 0.019993 * s(2 * m) + 0.000289 * s(3 * m)
        val lams = l0 + cs
        val eps = 23.439291 - 0.0130042 * t

        fun equatorial(l: Double, b: Double): Pair<Double, Double> {
            val ra = atan2(s(l) * c(eps) - tan(rad(b)) * s(eps), c(l))
            val dec = asin(s(b) * c(eps) + c(b) * s(eps) * s(l))
            return ra to dec
        }
        val (am, decm) = equatorial(lam, bet)
        val (asun, decs) = equatorial(lams, 0.0)

        // Fase
        val cpsi = sin(decs) * sin(decm) + cos(decs) * cos(decm) * cos(asun - am)
        val psi = acos(cpsi.coerceIn(-1.0, 1.0))
        val rSun = 149_598_000.0
        val i = atan2(rSun * sin(psi), dist - rSun * cos(psi))
        val k = (1 + cos(i)) / 2

        // Angolo di posizione del lembo illuminato (da nord verso est)
        val chi = atan2(
            cos(decs) * sin(asun - am),
            sin(decs) * cos(decm) - cos(decs) * sin(decm) * cos(asun - am)
        )

        // Angolo parallattico: dove sta lo zenit rispetto al nord celeste
        val gmst = norm(280.46061837 + 360.98564736629 * d)
        val h = rad(gmst + lonDeg) - am
        val lat = rad(latDeg)
        val q = atan2(sin(h), tan(lat) * cos(decm) - sin(decm) * cos(h))
        val alt = asin(sin(lat) * sin(decm) + cos(lat) * cos(decm) * cos(h))

        return State(
            illuminated = k,
            phaseAngle = i,
            brightLimbDeg = norm(deg(chi - q)).toFloat(),
            northDeg = norm(deg(-q)).toFloat(),
            waxing = norm(lam - lams) < 180.0,
            altitudeDeg = deg(alt)
        )
    }
}
