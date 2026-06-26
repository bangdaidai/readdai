package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.BookplateTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface BookplateTemplateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: BookplateTemplate): Long

    @Update
    suspend fun update(template: BookplateTemplate)

    @Delete
    suspend fun delete(template: BookplateTemplate)

    @Query("SELECT * FROM bookplateTemplates ORDER BY updateTime DESC")
    fun flowAll(): Flow<List<BookplateTemplate>>

    @Query("SELECT * FROM bookplateTemplates ORDER BY updateTime DESC")
    suspend fun getAll(): List<BookplateTemplate>

    @Query("SELECT * FROM bookplateTemplates WHERE id = :id")
    suspend fun getById(id: Long): BookplateTemplate?

    @Query("SELECT * FROM bookplateTemplates WHERE isBuiltin = 1 LIMIT 1")
    suspend fun getBuiltin(): BookplateTemplate?

    @Query("SELECT * FROM bookplateTemplates WHERE isBuiltin = 1 ORDER BY id ASC")
    suspend fun getBuiltins(): List<BookplateTemplate>

    @Query("DELETE FROM bookplateTemplates WHERE isBuiltin = 1")
    suspend fun deleteBuiltin()

    @Query("DELETE FROM bookplateTemplates WHERE isBuiltin = 1 AND id != :keepId")
    suspend fun deleteBuiltinExcept(keepId: Long)

    @Query("DELETE FROM bookplateTemplates WHERE isBuiltin = 1 AND id NOT IN (:keepIds)")
    suspend fun deleteBuiltinNotIn(keepIds: List<Long>)

    @Query("DELETE FROM bookplateTemplates WHERE id = :id")
    suspend fun deleteById(id: Long)
}
