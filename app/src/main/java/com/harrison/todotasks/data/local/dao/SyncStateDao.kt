package com.harrison.todotasks.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import com.harrison.todotasks.data.local.entities.SyncStateEntity

/** Reads and writes the single row of the `sync_state` table. */
@Dao
interface SyncStateDao {

    /** Returns the highest server version seen, or 0 if nothing has been synced. */
    @Query(
        "SELECT COALESCE((SELECT cursor FROM sync_state WHERE id = ${SyncStateEntity.ROW_ID}), 0)",
    )
    suspend fun getCursor(): Long

    /** Stores [cursor] as the highest server version seen. */
    @Query(
        "INSERT OR REPLACE INTO sync_state (id, cursor) " +
            "VALUES (${SyncStateEntity.ROW_ID}, :cursor)",
    )
    suspend fun setCursor(cursor: Long)
}
