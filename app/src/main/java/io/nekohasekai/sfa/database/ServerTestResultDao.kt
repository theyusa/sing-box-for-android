package io.nekohasekai.sfa.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerTestResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(result: ServerTestResult): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(results: List<ServerTestResult>)

    @Update
    fun update(result: ServerTestResult)

    @Delete
    fun delete(result: ServerTestResult)

    @Delete
    fun deleteAll(results: List<ServerTestResult>)

    @Query("DELETE FROM server_test_results WHERE profileId = :profileId")
    fun deleteByProfileId(profileId: Long)

    @Query("DELETE FROM server_test_results")
    fun clear()

    @Query("SELECT * FROM server_test_results WHERE id = :id")
    fun get(id: Long): ServerTestResult?

    @Query("SELECT * FROM server_test_results WHERE profileId = :profileId ORDER BY tested_at DESC")
    fun getByProfileId(profileId: Long): List<ServerTestResult>

    @Query("SELECT * FROM server_test_results WHERE profileId = :profileId ORDER BY tested_at DESC")
    fun getByProfileIdFlow(profileId: Long): Flow<List<ServerTestResult>>

    @Query("SELECT * FROM server_test_results WHERE profileId = :profileId AND serverTag = :serverTag")
    fun getByProfileAndServer(profileId: Long, serverTag: String): ServerTestResult?

    @Query("SELECT * FROM server_test_results WHERE profileId = :profileId AND is_success = 1 ORDER BY latency_ms ASC LIMIT 1")
    fun getBestServerByProfile(profileId: Long): ServerTestResult?

    @Query("SELECT * FROM server_test_results WHERE is_success = 1 ORDER BY tested_at DESC LIMIT :limit")
    fun getRecentSuccessfulResults(limit: Int = 50): List<ServerTestResult>

    @Query("SELECT * FROM server_test_results WHERE tested_at < :timestamp")
    fun getOlderThan(timestamp: Long): List<ServerTestResult>

    @Query("DELETE FROM server_test_results WHERE tested_at < :timestamp")
    fun deleteOlderThan(timestamp: Long)
}
