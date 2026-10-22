package it.emanuele.gmtbezel

import android.os.SystemClock
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import java.time.Instant

/** Riceve dal sistema i campioni del battito (anche con l'app chiusa). */
class HeartListenerService : PassiveListenerService() {

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val boot = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
        var changed = false
        for (dp in dataPoints.getData(DataType.HEART_RATE_BPM)) {
            if (HeartStore.add(this, dp.value, dp.getTimeInstant(boot))) changed = true
        }
        if (changed) HeartStore.requestComplicationUpdate(this)
    }
}
