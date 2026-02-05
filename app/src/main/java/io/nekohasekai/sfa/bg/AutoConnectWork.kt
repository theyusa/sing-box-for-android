package io.nekohasekai.sfa.bg

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.preference.AutoConnectPreferences
import io.nekohasekai.sfa.test.AutoConnectSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AutoConnectWork {
    companion object {
        private const val WORK_NAME = "AutoConnect"
        private const val TAG = "AutoConnectWork"

        suspend fun reconfigureWorker() {
            runCatching {
                reconfigureWorker0()
            }.onFailure {
                Log.e(TAG, "reconfigureWorker", it)
            }
        }

        private suspend fun reconfigureWorker0() {
            val settings = AutoConnectPreferences.toSettings()

            if (!settings.enabled || !BoxService.isServiceRunning()) {
                WorkManager.getInstance(Application.application).cancelUniqueWork(WORK_NAME)
                Log.d(TAG, "Auto-connect worker disabled or service not running")
                return
            }

            val intervalMinutes = (settings.testInterval / 1000 / 60).toInt()
                .coerceAtLeast(15)

            WorkManager.getInstance(Application.application).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequest.Builder(AutoConnectTask::class.java, intervalMinutes.toLong(), TimeUnit.MINUTES)
                    .apply {
                        setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
                        setConstraints(
                            androidx.work.Constraints.Builder()
                                .setRequiresDeviceIdle(false)
                                .setRequiresCharging(false)
                                .build(),
                        )
                    }
                    .build(),
            )

            Log.d(TAG, "Auto-connect worker reconfigured (interval: ${intervalMinutes}min)")
        }
    }

    class AutoConnectTask(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            return withContext(Dispatchers.IO) {
                try {
                    val settings = AutoConnectPreferences.toSettings()

                    if (!settings.enabled) {
                        Log.d(TAG, "Auto-connect is disabled, skipping")
                        return@withContext Result.success()
                    }

                    if (!BoxService.isServiceRunning()) {
                        Log.d(TAG, "Service is not running, skipping")
                        return@withContext Result.success()
                    }

                    Log.d(TAG, "Running auto-connect task")

                    val selector = AutoConnectSelector(applicationContext)
                    val bestServer = selector.selectBestServer()

                    if (bestServer != null) {
                        try {
                            val client = Libbox.newStandaloneCommandClient()
                            Log.i(TAG, "Switching to better server: ${bestServer.serverTag} (latency: ${bestServer.latencyMs}ms)")
                            client.selectOutbound("main", bestServer.serverTag)
                            client.closeConnections()
                            Log.i(TAG, "Successfully switched to ${bestServer.serverTag}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to switch server", e)
                            return@withContext Result.retry()
                        }
                    } else {
                        Log.d(TAG, "No better server found")
                    }

                    Result.success()
                } catch (e: Exception) {
                    Log.e(TAG, "Auto-connect task failed", e)
                    Result.failure()
                }
            }
        }
    }
}
