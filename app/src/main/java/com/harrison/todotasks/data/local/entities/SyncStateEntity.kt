package com.harrison.todotasks.data.local.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * The single row holding sync progress.
 *
 * @property id Fixed primary key, which limits the table to one row.
 * @property cursor The highest server version seen.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = ROW_ID,
    val cursor: Long,
) {
    companion object {
        /** Primary key of the only row in the table. */
        const val ROW_ID = 0
    }
}
