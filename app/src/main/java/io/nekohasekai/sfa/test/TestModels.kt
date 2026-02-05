package io.nekohasekai.sfa.test

enum class ErrorType {
    TIMEOUT,
    CONNECTION_REFUSED,
    DNS_ERROR,
    SNI_MISMATCH,
    NETWORK_ERROR,
    INVALID_CONFIG,
    AUTHENTICATION_FAILED,
    UNKNOWN_ERROR,
    ;

    companion object {
        fun fromException(exception: Throwable): ErrorType {
            val message = exception.message?.lowercase() ?: return UNKNOWN_ERROR

            return when {
                message.contains("timeout") -> TIMEOUT
                message.contains("connection refused") -> CONNECTION_REFUSED
                message.contains("dns") || message.contains("hostname") -> DNS_ERROR
                message.contains("sni") || message.contains("certificate") -> SNI_MISMATCH
                message.contains("network") -> NETWORK_ERROR
                message.contains("config") || message.contains("json") -> INVALID_CONFIG
                message.contains("auth") || message.contains("credentials") -> AUTHENTICATION_FAILED
                else -> UNKNOWN_ERROR
            }
        }
    }
}

sealed class TestResult {
    data class Success(
        val latencyMs: Int,
        val testUrl: String,
    ) : TestResult()

    data class Failed(
        val errorType: ErrorType,
        val errorMessage: String,
        val testUrl: String,
    ) : TestResult()

    data object Testing : TestResult()

    data object NotTested : TestResult()
}

data class ServerTestRequest(
    val serverTag: String,
    val profileId: Long,
    val configJson: String,
    val timeoutMs: Int = 5000,
)

data class ServerTestResponse(
    val serverTag: String,
    val profileId: Long,
    val result: TestResult,
    val testedAt: Long = System.currentTimeMillis(),
)
