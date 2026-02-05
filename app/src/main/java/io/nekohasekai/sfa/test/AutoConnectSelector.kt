package io.nekohasekai.sfa.test

import android.content.Context
import android.util.Log
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.database.ProfileManager
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

            Log.i(TAG, "Best server selected: ${bestServer.serverTag} (score: ${bestServer.score}, latency: ${bestServer.latencyMs}ms)")

            bestServer.testResult
        }
    }

    suspend fun selectBestServerInGroup(groupTag: String, profileId: Long): String? = withContext(Dispatchers.IO) {
        try {
            val settings = AutoConnectPreferences.toSettings()

            val client = Libbox.newStandaloneCommandClient()
            client.urlTest(groupTag)

            var retries = 0
            val maxRetries = 10
            val retryDelayMs = 500L

            while (retries < maxRetries) {
                val results = getAllTestResults()
                    .filter { it.isSuccess }
                    .filter { it.profileId == profileId }

                if (results.isNotEmpty()) {
                    val bestResult = results.minByOrNull { it.latencyMs ?: Int.MAX_VALUE }

                    if (bestResult != null && bestResult.latencyMs != null) {
                        val latency = bestResult.latencyMs!!
                        if (latency >= settings.minLatencyThreshold &&
                            latency <= settings.maxLatencyThreshold
                        ) {
                            Log.i(TAG, "Best server in group $groupTag: ${bestResult.serverTag} (latency: ${latency}ms)")

                            val selected = Libbox.newStandaloneCommandClient().selectOutbound(groupTag, bestResult.serverTag)
                            if (selected) {
                                return@withContext bestResult.serverTag
                            }
                        }
                    }
                }

                retries++
                if (retries < maxRetries) {
                    kotlinx.coroutines.delay(retryDelayMs)
                }
            }

            Log.w(TAG, "No suitable server found in group: $groupTag after $maxRetries retries")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting best server in group: $groupTag", e)
            null
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
        val dao = ProfileManager.instance.serverTestResultDao()
        return dao.getRecentSuccessfulResults()
    }

    private suspend fun getCurrentProfileId(): Long = io.nekohasekai.sfa.database.Settings.selectedProfile
}
