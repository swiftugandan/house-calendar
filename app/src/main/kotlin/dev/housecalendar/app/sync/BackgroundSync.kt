package dev.housecalendar.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.housecalendar.app.HouseCalendarApp
import dev.housecalendar.app.KeyState
import dev.housecalendar.contracts.DeviceRole
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * SPEC FR-44: a phone brings the kitchen display up to date in the background, every 15 minutes on an
 * unmetered network (Android's minimum periodic interval [SPEC R11]). Displays don't need it: they listen
 * whenever they run.
 */
object BackgroundSync {
    private const val WORK = "background-sync"
    private const val PERIOD_MINUTES = 15L

    fun update(context: Context, enabled: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

/** One background pass: find the family's devices on this Wi-Fi and sync with the ones this phone dials. */
class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as HouseCalendarApp).graph
        val held = graph.key.first { it !is KeyState.Loading } as? KeyState.Held ?: return Result.success()
        if (graph.role() == DeviceRole.Display) return Result.success()
        graph.sync.runOnce(graph.localNetwork, held.family, held.key, graph.deviceName(), DISCOVER_MS)
        // Failures are in the sync history; the next period simply tries again.
        return Result.success()
    }

    private companion object {
        /** Long enough for mDNS to answer on a busy home network; the display stops discovery early. */
        const val DISCOVER_MS = 20_000L
    }
}
