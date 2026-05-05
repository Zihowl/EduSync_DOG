package dev.zihowl.dog.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.zihowl.dog.data.model.ManualEvent

@Dao
interface ManualEventDao {
    @Query("SELECT * FROM manual_events")
    fun getAll(): LiveData<List<ManualEvent>>

    @Query("SELECT * FROM manual_events")
    fun getAllList(): List<ManualEvent>

    @Query("SELECT * FROM manual_events WHERE id = :id")
    fun getById(id: Int): ManualEvent?

    @Query("SELECT * FROM manual_events WHERE dayOfWeek = :dayOfWeek")
    fun getByDayOfWeek(dayOfWeek: Int): List<ManualEvent>

    @Query("SELECT * FROM manual_events WHERE date = :date")
    fun getByDate(date: Long): List<ManualEvent>

    @Query("SELECT * FROM manual_events WHERE owner = :owner")
    fun getAllForOwner(owner: String): List<ManualEvent>

    @Insert
    fun insert(event: ManualEvent): Long

    @Update
    fun update(event: ManualEvent): Int

    @Delete
    fun delete(event: ManualEvent): Int

    @Delete
    fun deleteAll(events: List<ManualEvent>): Int
}
