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

    @Query("SELECT * FROM subjects WHERE owner = :owner")
    fun getAllForOwnerLive(owner: String): LiveData<List<Subject>>

    @Query("SELECT * FROM subjects WHERE name = :name AND owner = :owner LIMIT 1")
    fun getByNameForOwner(name: String, owner: String): Subject?

    @Query("SELECT COUNT(*) FROM subjects WHERE owner = :owner")
    fun countForOwner(owner: String): Int

    @Query("SELECT * FROM subjects WHERE isOfficial = 1")
    fun getOfficialList(): List<Subject>

    @Query("DELETE FROM subjects WHERE isOfficial = 1")
    fun deleteAllOfficial(): Int

    @Query("DELETE FROM subjects WHERE isOfficial = 1 AND owner = :owner")
    fun deleteAllOfficialForOwner(owner: String): Int

    @Query("DELETE FROM subjects WHERE owner = :owner")
    fun deleteByOwner(owner: String): Int

    @Query("UPDATE subjects SET owner = :to WHERE owner = :from")
    fun reassignOwner(from: String, to: String): Int

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
