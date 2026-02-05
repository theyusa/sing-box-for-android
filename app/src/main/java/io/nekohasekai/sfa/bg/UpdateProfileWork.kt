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
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.utils.HTTPClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit

class UpdateProfileWork {
    companion object {
        private const val WORK_NAME = "UpdateProfile"
        private const val TAG = "UpdateProfileWork"

        suspend fun reconfigureUpdater() {
            runCatching {
                reconfigureUpdater0()
            }.onFailure {
                Log.e(TAG, "reconfigureUpdater", it)
            }
        }

        private suspend fun reconfigureUpdater0() {
            val remoteProfiles =
                ProfileManager.list()
                    .filter { it.typed.type == TypedProfile.Type.Remote && it.typed.autoUpdate }
            if (remoteProfiles.isEmpty()) {
                WorkManager.getInstance(Application.application).cancelUniqueWork(WORK_NAME)
                return
            }

            var minDelay =
                remoteProfiles.minByOrNull { it.typed.autoUpdateInterval }!!.typed.autoUpdateInterval.toLong()
            val nowSeconds = System.currentTimeMillis() / 1000L
            val minInitDelay =
                remoteProfiles.minOf { (it.typed.autoUpdateInterval * 60) - (nowSeconds - (it.typed.lastUpdated.time / 1000L)) }
            if (minDelay < 15) minDelay = 15
            WorkManager.getInstance(Application.application).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequest.Builder(UpdateTask::class.java, minDelay, TimeUnit.MINUTES)
                    .apply {
                        if (minInitDelay > 0) setInitialDelay(minInitDelay, TimeUnit.SECONDS)
                        setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
                    }
                    .build(),
            )
        }
    }

    class UpdateTask(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            var selectedProfileUpdated = false
            val remoteProfiles =
                ProfileManager.list()
                    .filter { it.typed.type == TypedProfile.Type.Remote && it.typed.autoUpdate }
            if (remoteProfiles.isEmpty()) return Result.success()
            var success = true
            val selectedProfile = Settings.selectedProfile
            for (profile in remoteProfiles) {
                val lastSeconds =
                    (System.currentTimeMillis() - profile.typed.lastUpdated.time) / 1000L
                if (lastSeconds < profile.typed.autoUpdateInterval * 60) {
                    continue
                }
                try {
                    val content = HTTPClient().use { it.getString(profile.typed.remoteURL) }
                    Libbox.checkConfig(content)
                    
                    // Force Resolve aktifse domain'leri IP'ye çevir
                    val finalContent = if (profile.typed.forceResolve) {
                        resolveDomainToIP(content)
                    } else {
                        content
                    }
                    
                    val file = File(profile.typed.path)
                    if (file.readText() != finalContent) {
                        File(profile.typed.path).writeText(finalContent)
                        if (profile.id == selectedProfile) {
                            selectedProfileUpdated = true
                        }
                    }
                    profile.typed.lastUpdated = Date()
                    ProfileManager.update(profile)
                } catch (e: Exception) {
                    Log.e(TAG, "update profile ${profile.name}", e)
                    success = false
                }
            }
            if (selectedProfileUpdated) {
                runCatching {
                    Libbox.newStandaloneCommandClient().serviceReload()
                }
            
            }
            return if (success) {
                Result.success()
            } else {
                Result.retry()
            }
        }

        private suspend fun resolveDomainToIP(configJson: String): String {  
            return withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val jsonObject = org.json.JSONObject(configJson)
                    val outbounds = jsonObject.optJSONArray("outbounds") ?: return@withContext configJson
                    
                    val skipTypes = setOf(
                        "selector", "urltest", "direct", "block",
                        "dns", "reject", "blackhole", "loopback"
                    )
                    
                    var resolvedCount = 0
                    var skippedCount = 0
                    var errorCount = 0
                    
                    for (i in 0 until outbounds.length()) {
                        val outbound = outbounds.getJSONObject(i)
                        val server = outbound.optString("server", "")
                        val type = outbound.optString("type", "")
                        
                        if (type in skipTypes) {
                            Log.d(TAG, "⚡ Skipping '$type' outbound: ${outbound.optString("tag", "unnamed")}")
                            skippedCount++
                            continue
                        }
                        
                        if (server.isNotEmpty() && !isIPAddress(server)) {
                            Log.i(TAG, "🔍 Resolving domain: $server (type: $type, tag: ${outbound.optString("tag", "unnamed")})")
                            val resolvedIP = resolveDomain(server)
                            if (resolvedIP != null) {
                                outbound.put("server", resolvedIP)
                                resolvedCount++
                                Log.i(TAG, "✅ Resolved $server -> $resolvedIP (type: $type)")
                            } else {
                                errorCount++
                                Log.w(TAG, "⚠️ Failed to resolve: $server (keeping original, type: $type)")
                            }
                        }
                    }
                    
                    Log.i(TAG, "📊 Summary: $resolvedCount resolved, $skippedCount skipped, $errorCount errors")
                    jsonObject.toString(4) // Pretty-print with 4-space indentation
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error resolving domains during update", e)
                    configJson
                }
            }
        }

        private fun isIPAddress(address: String): Boolean {
            val ipv4Pattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
            val ipv6Pattern = "^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$"
            return address.matches(ipv4Pattern.toRegex()) || address.matches(ipv6Pattern.toRegex())
        }

        private fun resolveDomain(domain: String): String? {
            return try {
                val addresses = java.net.InetAddress.getAllByName(domain)
                addresses.firstOrNull()?.hostAddress
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve $domain", e)
                null
            }
        }
    }  
}  
