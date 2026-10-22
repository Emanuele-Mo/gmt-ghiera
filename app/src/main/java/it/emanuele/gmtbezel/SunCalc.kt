package it.emanuele.gmtbezel

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** Risultato del calcolo: ore locali decimali (es. 7.5 = 7:30). */
sealed class SunDay {
    data class Normal(val sunrise: Double, val sunset: Double) : SunDay()
    object PolarDay : SunDay()    // il sole non tramonta: tutto giorno
    object PolarNight : SunDay()  // il sole non sorge: tutto notte
}

/** Alba/tramonto con l'algoritmo dell'Almanac for Computers (errore ~1-2 minuti). */
object SunCalc {

    private const val ZENITH = 90.833 // centro del sole sull'orizzonte + rifrazione

    fun compute(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): SunDay {
        val n = date.dayOfYear
        val rise = utHour(n, lat, lon, true)
        val set = utHour(n, lat, lon, false)
        if (rise == null || set == null) {
            // cosH > 1 -> notte polare, cosH < -1 -> giorno polare
            return if (cosH(n, lat, lon, true) > 1) SunDay.PolarNight else SunDay.PolarDay
        }
        return SunDay.Normal(toLocal(date, rise, zone), toLocal(date, set, zone))
    }

    private fun toLocal(date: LocalDate, utHour: Double, zone: ZoneId): Double {
        val utc = ZonedDateTime.of(date.atStartOfDay(), ZoneOffset.UTC)
            .plusSeconds((utHour * 3600).toLong())
        val z = utc.withZoneSameInstant(zone)
        return z.hour + z.minute / 60.0 + z.second / 3600.0
    }

    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun norm(x: Double, m: Double) = ((x % m) + m) % m

    private class Sun(val t: Double, val ra: Double, val sinDec: Double, val cosDec: Double)

    private fun sun(n: Int, lon: Double, rise: Boolean): Sun {
        val lngHour = lon / 15.0
        val t = n + ((if (rise) 6.0 else 18.0) - lngHour) / 24.0
        val m = 0.9856 * t - 3.289
        val l = norm(m + 1.916 * sin(rad(m)) + 0.020 * sin(rad(2 * m)) + 282.634, 360.0)
        var ra = norm(deg(atan(0.91764 * tan(rad(l)))), 360.0)
        ra += floor(l / 90.0) * 90.0 - floor(ra / 90.0) * 90.0
        ra /= 15.0
        val sinDec = 0.39782 * sin(rad(l))
        val cosDec = cos(asin(sinDec))
        return Sun(t, ra, sinDec, cosDec)
    }

    private fun cosH(n: Int, lat: Double, lon: Double, rise: Boolean): Double {
        val s = sun(n, lon, rise)
        return (cos(rad(ZENITH)) - s.sinDec * sin(rad(lat))) / (s.cosDec * cos(rad(lat)))
    }

    private fun utHour(n: Int, lat: Double, lon: Double, rise: Boolean): Double? {
        val s = sun(n, lon, rise)
        val c = (cos(rad(ZENITH)) - s.sinDec * sin(rad(lat))) / (s.cosDec * cos(rad(lat)))
        if (c > 1 || c < -1) return null
        val h = (if (rise) 360.0 - deg(acos(c)) else deg(acos(c))) / 15.0
        val tLocal = h + s.ra - 0.06571 * s.t - 6.622
        return norm(tLocal - lon / 15.0, 24.0)
    }
}
