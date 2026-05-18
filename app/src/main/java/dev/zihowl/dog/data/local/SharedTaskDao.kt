package dev.zihowl.dog.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.zihowl.dog.data.model.SharedTaskInbox

@Dao
interface SharedTaskDao {
    @Query("SELECT * FROM shared_task_inbox WHERE owner = :owner ORDER BY createdAt DESC")
    fun getInboxForOwnerLive(owner: String): LiveData<List<SharedTaskInbox>>

    @Query("SELECT * FROM shared_task_inbox WHERE owner = :owner")
    fun getInboxForOwner(owner: String): List<SharedTaskInbox>

    @Query("SELECT sharedTaskId FROM shared_task_inbox WHERE owner = :owner")
    fun getKnownIds(owner: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(item: SharedTaskInbox)

    @Query("UPDATE shared_task_inbox SET status = :status WHERE sharedTaskId = :id")
    fun updateStatus(id: String, status: String)

    @Query("DELETE FROM shared_task_inbox WHERE owner = :owner")
    fun deleteByOwner(owner: String)

    @Query("UPDATE shared_task_inbox SET owner = :to WHERE owner = :from")
    fun reassignOwner(from: String, to: String)
}
