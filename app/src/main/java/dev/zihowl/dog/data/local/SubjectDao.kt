package dev.zihowl.dog.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.zihowl.dog.data.model.Subject

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects")
    fun getAll(): LiveData<List<Subject>>

    @Query("SELECT * FROM subjects")
    fun getAllList(): List<Subject>

    @Query("SELECT * FROM subjects WHERE id = :id")
    fun getById(id: Int): Subject?

    @Query("SELECT * FROM subjects WHERE name = :name LIMIT 1")
    fun getByName(name: String): Subject?

    @Query("SELECT * FROM subjects WHERE owner = :owner")
    fun getAllForOwner(owner: String): List<Subject>

    @Query("SELECT EXISTS(SELECT 1 FROM subjects WHERE name = :name)")
    fun exists(name: String): Boolean

    @Insert
    fun insert(subject: Subject): Long

    @Update
    fun update(subject: Subject): Int

    @Delete
    fun delete(subject: Subject): Int

    @Query("DELETE FROM subjects WHERE id = :id")
    fun deleteById(id: Int): Int

    @Query("DELETE FROM subjects WHERE id IN (:ids)")
    fun deleteByIds(ids: List<Int>): Int
}
