package io.nekohasekai.sfa.bg

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.database.preference.AutoConnectPreferences
import io.nekohasekai.sfa.test.AutoConnectSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class ConnectionMonitor : LifecycleEventObserver {
    companion object {
        private const val TAG = "ConnectionMonitor"
        private const val CONNECTION_CHECK_INTERVAL = 30_000L
        private const val CONNECTION_TIMEOUT = 10_000L

        @Volatile
        private var instance: ConnectionMonitor? = null

        fun getInstance(): ConnectionMonitor = instance ?: synchronized(this) {
            instance ?: ConnectionMonitor().also { instance = it }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val failedAttempts = AtomicInteger(0)
    private var isMonitoring = false
    private var lastSuccessfulCheck = 0L

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStateChanged(source: androidx.lifecycle.LifecycleOwner, event: androidx.lifecycle.Lifecycle.Event) {
        when (event) {
            androidx.lifecycle.Lifecycle.Event.ON_START -> {
                Log.d(TAG, "App started, starting connection monitor")
                startMonitoring()
            }
            androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                Log.d(TAG, "App stopped, pausing connection monitor")
                stopMonitoring()
            }
            else -> {}
        }
    }

    fun startMonitoring() {
        if (isMonitoring) return

        val settings = AutoConnectPreferences.toSettings()
        if (!settings.enabled) {
            Log.d(TAG, "Auto-connect is disabled")
            return
        }

        isMonitoring = true
        scope.launch {
            while (isActive && isMonitoring) {
                if (BoxService.isServiceRunning()) {
                    checkConnection()
                }

                delay(CONNECTION_CHECK_INTERVAL)
            }
        }

        Log.d(TAG, "Connection monitoring started")
    }

    fun stopMonitoring() {
        isMonitoring = false
        failedAttempts.set(0)
        Log.d(TAG, "Connection monitoring stopped")
    }

    fun onConnectionAttempt(success: Boolean) {
        if (success) {
            failedAttempts.set(0)
            lastSuccessfulCheck = System.currentTimeMillis()
            Log.d(TAG, "Connection attempt successful, reset failure counter")
        } else {
            val attempts = failedAttempts.incrementAndGet()
            Log.w(TAG, "Connection attempt failed ($attempts/${AutoConnectPreferences.failureThreshold})")

            val settings = AutoConnectPreferences.toSettings()
            if (settings.autoSwitchEnabled && attempts >= settings.failureThreshold) {
                Log.i(TAG, "Failure threshold reached, attempting to switch server")
                attemptServerSwitch()
            }
        }
    }

    private suspend fun checkConnection() {
        try {
            val client = Libbox.newStandaloneCommandClient()
            val result = client.testOutbound(
                "main",
                "https://www.gstatic.com/generate_204",
                CONNECTION_TIMEOUT,
            )

            if (result > 0) {
                onConnectionAttempt(true)
            } else {
                onConnectionAttempt(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection check failed", e)
            onConnectionAttempt(false)
        }
    }

    private fun attemptServerSwitch() {
        scope.launch(Dispatchers.IO) {
            try {
                val selector = AutoConnectSelector(Application.application)
                val bestServer = selector.selectBestServer()

                if (bestServer != null) {
                    Log.i(TAG, "Switching to best server: ${bestServer.serverTag}")

                    val client = Libbox.newStandaloneCommandClient()
                    client.selectOutbound("main", bestServer.serverTag)

                    failedAttempts.set(0)
                } else {
                    Log.w(TAG, "No better server found for switching")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to switch server", e)
            }
        }
    }

    fun getCurrentFailureCount(): Int = failedAttempts.get()

    fun getLastSuccessfulCheck(): Long = lastSuccessfulCheck
}
