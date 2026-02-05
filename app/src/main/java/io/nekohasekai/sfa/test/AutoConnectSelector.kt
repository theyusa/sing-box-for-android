package io.nekohasekai.sfa.test

import android.content.Context
import android.util.Log
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.database.ProfileDatabase
import io.nekohasekai.sfa.database.ServerTestResult
import io.nekohasekai.sfa.database.preference.AutoConnectPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ServerScore(
    val serverTag: String,
    val profileId: Long,
    val score: Int,
    val latencyMs: Int?,
    val testResult: ServerTestResult?,
)

class AutoConnectSelector(private val context: Context) {
    companion object {
        private const val TAG = "AutoConnectSelector"
        private const val PROFILE_PRIORITY_WEIGHT = 1000
        private const val MAX_SCORE = Int.MAX_VALUE
    }

    suspend fun selectBestServer(): ServerTestResult? {
        return withContext(Dispatchers.IO) {
            val settings = AutoConnectPreferences.toSettings()

            val allResults = getAllTestResults()

            if (allResults.isEmpty()) {
                Log.w(TAG, "No test results available")
                return@withContext null
            }

            val currentServerTag = getCurrentServerTag()
            val profileId = getCurrentProfileId()

            val scoredServers = allResults
                .filter { it.isSuccess }
                .filter { result ->
                    result.latencyMs?.let { latency ->
                        latency >= settings.minLatencyThreshold &&
                            latency <= settings.maxLatencyThreshold
                    } ?: false
                }
                .map { result ->
                    ServerScore(
                        serverTag = result.serverTag,
                        profileId = result.profileId,
                        score = calculateScore(result, settings, profileId),
                        latencyMs = result.latencyMs,
                        testResult = result,
                    )
                }
                .sortedBy { it.score }

            if (scoredServers.isEmpty()) {
                Log.w(TAG, "No servers meet the criteria")
                return@withContext null
            }

            val bestServer = scoredServers.first()

            if (bestServer.serverTag == currentServerTag) {
                Log.d(TAG, "Current server is already the best: $currentServerTag")
                return@withContext null
            }

            Log.i(TAG, "Best server selected: ${bestServer.serverTag} (score: ${bestServer.score}, latency: ${bestServer.latencyMs}ms)")

            bestServer.testResult
        }
    }

    suspend fun selectBestServerInGroup(groupTag: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val settings = AutoConnectPreferences.toSettings()

                val client = Libbox.newStandaloneCommandClient()
                client.urlTest(groupTag)
                kotlinx.coroutines.delay(2000)

                null
            } catch (e: Exception) {
                Log.e(TAG, "Error selecting best server in group: $groupTag", e)
                null
            }
        }
    }

    private fun calculateScore(
        result: ServerTestResult,
        settings: AutoConnectSettings,
        currentProfileId: Long,
    ): Int {
        if (!result.isSuccess) {
            return MAX_SCORE
        }

        val latency = result.latencyMs ?: return MAX_SCORE

        if (latency < settings.minLatencyThreshold || latency > settings.maxLatencyThreshold) {
            return MAX_SCORE
        }

        var score = latency

        if (!settings.testAllProfiles) {
            if (result.profileId != currentProfileId) {
                score += PROFILE_PRIORITY_WEIGHT
            }
        }

        return score
    }

    private suspend fun getAllTestResults(): List<ServerTestResult> {
        val dao = ProfileDatabase.getInstance(context).serverTestResultDao()
        return dao.getRecentSuccessfulResults()
    }

    private suspend fun getCurrentServerTag(): String? {
        return try {
            val client = Libbox.newStandaloneCommandClient()
            val status = client.status
            status?.selectedOutbound
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current server tag", e)
            null
        }
    }

    private suspend fun getCurrentProfileId(): Long = io.nekohasekai.sfa.database.Settings.selectedProfile
}
