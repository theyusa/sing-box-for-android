package io.nekohasekai.sfa.test

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OutboundGroup
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.ServerTestResult
import io.nekohasekai.sfa.utils.CommandClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

interface ServerTester {
    suspend fun testServer(request: ServerTestRequest): ServerTestResponse

    suspend fun testServers(requests: List<ServerTestRequest>): List<ServerTestResponse>

    suspend fun testOutboundGroup(groupTag: String): Map<String, TestResult>
}

class LibboxServerTester(
    private val commandClient: io.nekohasekai.libbox.CommandClient? = null,
    private val testUrl: String = "https://www.gstatic.com/generate_204",
    private val timeoutMs: Int = 5000,
) : ServerTester {

    companion object {
        private const val TAG = "LibboxServerTester"
        private val testCache = ConcurrentHashMap<String, CachedTestResult>()

        data class CachedTestResult(
            val result: TestResult,
            val timestamp: Long,
        )

        fun clearCache() {
            testCache.clear()
        }

        fun isCacheValid(result: CachedTestResult, cacheTimeMs: Long = 60000): Boolean = System.currentTimeMillis() - result.timestamp < cacheTimeMs
    }

    override suspend fun testServer(request: ServerTestRequest): ServerTestResponse {
        return withContext(Dispatchers.IO) {
            val cacheKey = "${request.profileId}_${request.serverTag}"

            testCache[cacheKey]?.let { cached ->
                if (isCacheValid(cached)) {
                    return@withContext ServerTestResponse(
                        serverTag = request.serverTag,
                        profileId = request.profileId,
                        result = cached.result,
                    )
                }
            }

            val result = performTest(request)

            testCache[cacheKey] = CachedTestResult(result, System.currentTimeMillis())

            ServerTestResponse(
                serverTag = request.serverTag,
                profileId = request.profileId,
                result = result,
            )
        }
    }

    override suspend fun testServers(requests: List<ServerTestRequest>): List<ServerTestResponse> = requests.map { asyncTest(it) }

    private suspend fun asyncTest(request: ServerTestRequest): ServerTestResponse = testServer(request)

    override suspend fun testOutboundGroup(groupTag: String): Map<String, TestResult> {
        return withContext(Dispatchers.IO) {
            try {
                val client = commandClient ?: Libbox.newStandaloneCommandClient()

                client.urlTest(groupTag)

                kotlinx.coroutines.delay(1000)

                val groups = client.getGroups()

                for (group in groups) {
                    if (group.tag == groupTag) {
                        val results = mutableMapOf<String, TestResult>()
                        for (item in group.items) {
                            val result = when {
                                item.urlTestDelay > 0 -> {
                                    TestResult.Success(
                                        latencyMs = item.urlTestDelay,
                                        testUrl = testUrl,
                                    )
                                }

                                item.urlTestDelay < 0 -> {
                                    TestResult.Failed(
                                        errorType = ErrorType.UNKNOWN_ERROR,
                                        errorMessage = "Connection failed",
                                        testUrl = testUrl,
                                    )
                                }

                                else -> {
                                    TestResult.NotTested
                                }
                            }
                            results[item.tag] = result
                        }
                        return@withContext results
                    }
                }

                emptyMap()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error testing outbound group: $groupTag", e)
                emptyMap()
            }
        }
    }

    private suspend fun performTest(request: ServerTestRequest): TestResult = try {
        val client = commandClient ?: Libbox.newStandaloneCommandClient()

        val testResult = client.testOutbound(
            request.serverTag,
            testUrl,
            timeoutMs.toLong(),
        )

        if (testResult > 0) {
            TestResult.Success(
                latencyMs = testResult.toInt(),
                testUrl = testUrl,
            )
        } else {
            TestResult.Failed(
                errorType = ErrorType.CONNECTION_REFUSED,
                errorMessage = "Connection failed",
                testUrl = testUrl,
            )
        }
    } catch (e: Exception) {
        android.util.Log.e(TAG, "Test failed for ${request.serverTag}", e)
        TestResult.Failed(
            errorType = ErrorType.fromException(e),
            errorMessage = e.message ?: "Unknown error",
            testUrl = testUrl,
        )
    }
}
