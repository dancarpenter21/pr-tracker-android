package com.prtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE id = 1")
    fun observeProfile(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = 1")
    suspend fun getProfile(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface LiftDao {
    @Query("SELECT * FROM lifts ORDER BY archived ASC, major DESC, name ASC")
    fun observeLifts(): Flow<List<LiftEntity>>

    @Query("SELECT * FROM lifts WHERE archived = 0 ORDER BY major DESC, name ASC")
    suspend fun activeLifts(): List<LiftEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(lift: LiftEntity): Long

    @Update
    suspend fun update(lift: LiftEntity)

    @Query("UPDATE lifts SET archived = :archived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE lifts SET major = :major, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setMajor(id: Long, major: Boolean, updatedAt: Long = System.currentTimeMillis()): Int
}

@Dao
interface EntryDao {
    @Query(
        """
        SELECT lift_entries.*, lifts.name AS liftName
        FROM lift_entries
        INNER JOIN lifts ON lifts.id = lift_entries.liftId
        ORDER BY performedAt DESC, lift_entries.id DESC
        """,
    )
    fun observeEntries(): Flow<List<EntryWithLift>>

    @Query(
        """
        SELECT MAX(estimatedOneRmKg)
        FROM lift_entries
        WHERE liftId = :liftId
        """,
    )
    suspend fun bestEstimatedOneRm(liftId: Long): Double?

    @Insert
    suspend fun insert(entry: LiftEntryEntity): Long

    @Query("DELETE FROM lift_entries WHERE id = :id")
    suspend fun delete(id: Long): Int
}
