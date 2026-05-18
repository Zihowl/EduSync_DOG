package dev.zihowl.dog.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.zihowl.dog.data.model.Notification

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE owner = :owner ORDER BY timestamp DESC")
    fun getAllForOwnerLive(owner: String): LiveData<List<Notification>>

    @Query("SELECT * FROM notifications WHERE owner = :owner ORDER BY timestamp DESC")
    fun getAllForOwner(owner: String): List<Notification>

    @Query("SELECT COUNT(*) FROM notifications WHERE owner = :owner AND isRead = 0")
    fun unreadCountForOwnerLive(owner: String): LiveData<Int>

    @Query("SELECT COUNT(*) FROM notifications WHERE owner = :owner AND isRead = 0")
    fun unreadCountForOwner(owner: String): Int

    /**
     * Último valor registrado para un cambio (owner+materia+tipo). Permite
     * deduplicar: si coincide con el valor entrante no hubo cambio real desde
     * la última sincronización, así reconexiones repetidas no repiten avisos.
     */
    @Query(
        "SELECT newValue FROM notifications WHERE owner = :owner AND type = :type " +
            "AND subjectName = :subjectName ORDER BY timestamp DESC LIMIT 1"
    )
    fun latestValueFor(owner: String, type: String, subjectName: String): String?

    @Query("UPDATE notifications SET isRead = 1 WHERE owner = :owner")
    fun markAllReadForOwner(owner: String): Int

    @Query("DELETE FROM notifications WHERE owner = :owner")
    fun deleteByOwner(owner: String): Int

    @Query("DELETE FROM notifications WHERE id = :id")
    fun deleteById(id: Int): Int

    @Query("UPDATE notifications SET owner = :to WHERE owner = :from")
    fun reassignOwner(from: String, to: String): Int

    @Insert
    fun insert(notification: Notification): Long
}
