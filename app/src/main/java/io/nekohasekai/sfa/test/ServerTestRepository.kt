package io.nekohasekai.sfa.test

import io.nekohasekai.sfa.database.ProfileDatabase
import io.nekohasekai.sfa.database.ServerTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ServerTestRepository(
    private val database: ProfileDatabase,
    private val serverTester: ServerTester,
) {
    companion object {
        private const val TAG = "ServerTestRepository"
        private const val CACHE_VALIDITY_MS = TimeUnit.MINUTES.toMillis(5)
    }

    suspend fun testServer(request: ServerTestRequest): ServerTestResult {
        val response = serverTester.testServer(request)

        val dbResult = ServerTestResult(
            serverTag = request.serverTag,
            profileId = request.profileId,
            isSuccess = response.result is TestResult.Success,
            latencyMs = when (val result = response.result) {
                is TestResult.Success -> result.latencyMs
                else -> null
            },
            errorType = when (val result = response.result) {
                is TestResult.Failed -> result.errorType.name
                else -> null
            },
            errorMessage = when (val result = response.result) {
                is TestResult.Failed -> result.errorMessage
                else -> null
            },
            testedAt = response.testedAt,
        )

        database.serverTestResultDao().insert(dbResult)

        return dbResult
    }

    suspend fun testServers(requests: List<ServerTestRequest>): List<ServerTestResult> {
        val responses = serverTester.testServers(requests)

        val dbResults = responses.map { response ->
            ServerTestResult(
                serverTag = response.serverTag,
                profileId = response.profileId,
                isSuccess = response.result is TestResult.Success,
                latencyMs = when (val result = response.result) {
                    is TestResult.Success -> result.latencyMs
                    else -> null
                },
                errorType = when (val result = response.result) {
                    is TestResult.Failed -> result.errorType.name
                    else -> null
                },
                errorMessage = when (val result = response.result) {
                    is TestResult.Failed -> result.errorMessage
                    else -> null
                },
                testedAt = response.testedAt,
            )
        }

        database.serverTestResultDao().insertAll(dbResults)

        return dbResults
    }

    suspend fun testOutboundGroup(
        profileId: Long,
        groupTag: String,
    ): Map<String, ServerTestResult> {
        if (serverTester !is LibboxServerTester) {
            return emptyMap()
        }

        val results = serverTester.testOutboundGroup(groupTag)

        val dbResults = results.map { (serverTag, testResult) ->
            ServerTestResult(
                serverTag = serverTag,
                profileId = profileId,
                isSuccess = testResult is TestResult.Success,
                latencyMs = when (val result = testResult) {
                    is TestResult.Success -> result.latencyMs
                    else -> null
                },
                errorType = when (val result = testResult) {
                    is TestResult.Failed -> result.errorType.name
                    else -> null
                },
                errorMessage = when (val result = testResult) {
                    is TestResult.Failed -> result.errorMessage
                    else -> null
                },
            )
        }

        database.serverTestResultDao().insertAll(dbResults)

        return dbResults.associateBy { it.serverTag }
    }

    suspend fun getTestResult(profileId: Long, serverTag: String): ServerTestResult? {
        return withContext(Dispatchers.IO) {
            val cached = database.serverTestResultDao().getByProfileAndServer(profileId, serverTag)

            if (cached != null && isCacheValid(cached)) {
                return@withContext cached
            }

            cached
        }
    }

    fun getTestResultsFlow(profileId: Long): Flow<List<ServerTestResult>> = database.serverTestResultDao().getByProfileIdFlow(profileId)
        .flowOn(Dispatchers.IO)

    suspend fun getBestServer(profileId: Long): ServerTestResult? {
        return withContext(Dispatchers.IO) {
            val best = database.serverTestResultDao().getBestServerByProfile(profileId)

            if (best != null && isCacheValid(best)) {
                return@withContext best
            }

            best
        }
    }

    suspend fun getAllTestResults(): List<ServerTestResult> = withContext(Dispatchers.IO) {
        database.serverTestResultDao().getRecentSuccessfulResults()
    }

    suspend fun cleanupOldResults(maxAgeMs: Long = TimeUnit.HOURS.toMillis(24)) = withContext(Dispatchers.IO) {
        val cutoffTime = System.currentTimeMillis() - maxAgeMs
        database.serverTestResultDao().deleteOlderThan(cutoffTime)
    }

    suspend fun deleteProfileResults(profileId: Long) = withContext(Dispatchers.IO) {
        database.serverTestResultDao().deleteByProfileId(profileId)
    }

    suspend fun clearAllResults() = withContext(Dispatchers.IO) {
        database.serverTestResultDao().clear()
    }

    private fun isCacheValid(result: ServerTestResult): Boolean = System.currentTimeMillis() - result.testedAt < CACHE_VALIDITY_MS
}
