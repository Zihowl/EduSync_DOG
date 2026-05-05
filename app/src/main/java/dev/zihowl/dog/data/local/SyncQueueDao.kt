package dev.zihowl.dog.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import dev.zihowl.dog.data.model.SyncQueueItem

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE owner = :owner ORDER BY timestamp ASC")
    fun getAllForOwner(owner: String): LiveData<List<SyncQueueItem>>

    @Query("SELECT * FROM sync_queue WHERE owner = :owner ORDER BY timestamp ASC")
    fun getAllListForOwner(owner: String): List<SyncQueueItem>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE owner = :owner")
    fun getPendingCountForOwner(owner: String): Int

    @Insert
    fun insert(item: SyncQueueItem): Long

    @Delete
    fun delete(item: SyncQueueItem): Int

    @Query("DELETE FROM sync_queue WHERE owner = :owner")
    fun clearForOwner(owner: String): Int
}
