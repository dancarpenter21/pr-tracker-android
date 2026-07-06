package com.prtracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: Long = 1,
    val sex: String = "male",
    val preferredUnit: String = "lb",
    val bodyweightKg: Double? = null,
)

@Entity(
    tableName = "lifts",
    indices = [Index(value = ["name"], unique = true)],
)
data class LiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val archived: Boolean = false,
    val major: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "lift_entries",
    foreignKeys = [
        ForeignKey(
            entity = LiftEntity::class,
            parentColumns = ["id"],
            childColumns = ["liftId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("liftId"), Index("performedAt")],
)
data class LiftEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val liftId: Long,
    val performedAt: Long,
    val weightKg: Double,
    val originalUnit: String,
    val sets: Int,
    val reps: Int,
    val bodyweightKg: Double?,
    val estimatedOneRmKg: Double,
    val wilks: Double?,
    val volumeKg: Double,
    val isPr: Boolean,
    val notes: String = "",
)

data class EntryWithLift(
    val id: Long,
    val liftId: Long,
    val liftName: String,
    val performedAt: Long,
    val weightKg: Double,
    val originalUnit: String,
    val sets: Int,
    val reps: Int,
    val bodyweightKg: Double?,
    val estimatedOneRmKg: Double,
    val wilks: Double?,
    val volumeKg: Double,
    val isPr: Boolean,
    val notes: String,
)
