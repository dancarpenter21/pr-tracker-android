package com.prtracker.data

import android.content.Context
import androidx.room.Room
import com.prtracker.core.PrCore
import com.prtracker.core.Sex
import com.prtracker.core.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private var database = createDatabase()

    var repository = Repository(database)
        private set
    val driveAuthManager = DriveAuthManager(appContext)
    val driveBackupClient = DriveBackupClient()
    val databaseBackupManager = DatabaseBackupManager(
        context = appContext,
        databaseProvider = { database },
        closeDatabase = { database.close() },
        reopenDatabase = {
            database = createDatabase()
            repository = Repository(database)
        },
    )
    val driveBackupCoordinator = DriveBackupCoordinator(
        authManager = driveAuthManager,
        driveClient = driveBackupClient,
        databaseBackupManager = databaseBackupManager,
    )

    private fun createDatabase(): AppDatabase {
        return Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            DatabaseBackupManager.DATABASE_NAME,
        ).build()
    }
}

class Repository(private val db: AppDatabase) {
    val profile: Flow<ProfileEntity?> = db.profileDao().observeProfile()
    val lifts: Flow<List<LiftEntity>> = db.liftDao().observeLifts()
    val entries: Flow<List<EntryWithLift>> = db.entryDao().observeEntries()
    val appState: Flow<AppState> = combine(profile, lifts, entries) { profile, lifts, entries ->
        AppState(profile = profile ?: ProfileEntity(), lifts = lifts, entries = entries)
    }

    suspend fun seedDefaults() {
        if (db.profileDao().getProfile() == null) {
            db.profileDao().upsert(ProfileEntity())
        }
        listOf(
            LiftEntity(name = "Squat", major = true),
            LiftEntity(name = "Bench press", major = true),
            LiftEntity(name = "Deadlift", major = true),
            LiftEntity(name = "Overhead press"),
        ).forEach { db.liftDao().insert(it) }
    }

    suspend fun saveProfile(sex: Sex, preferredUnit: WeightUnit): Result<Boolean> {
        return runCatching {
            val currentProfile = db.profileDaoSnapshot()
            val updatedProfile = currentProfile.copy(
                sex = sex.jsonValue,
                preferredUnit = preferredUnit.jsonValue,
            )
            if (updatedProfile == currentProfile) {
                false
            } else {
                db.profileDao().upsert(updatedProfile)
                true
            }
        }
    }

    suspend fun addLift(name: String): Boolean {
        val cleanName = name.trim()
        return cleanName.isNotEmpty() && db.liftDao().insert(LiftEntity(name = cleanName)) != -1L
    }

    suspend fun toggleMajor(lift: LiftEntity) = db.liftDao().setMajor(lift.id, !lift.major) > 0
    suspend fun archiveLift(lift: LiftEntity) = db.liftDao().setArchived(lift.id, true) > 0
    suspend fun restoreLift(lift: LiftEntity) = db.liftDao().setArchived(lift.id, false) > 0
    suspend fun deleteEntry(id: Long) = db.entryDao().delete(id) > 0

    suspend fun addEntry(
        liftId: Long,
        weight: Double,
        unit: WeightUnit,
        sets: Int,
        reps: Int,
        bodyweight: Double?,
        notes: String,
        performedAt: Long = System.currentTimeMillis(),
    ): List<String> {
        val profile = db.profileDaoSnapshot()
        val previousBest = db.entryDao().bestEstimatedOneRm(liftId)
        val sex = profile.sex.toSex()
        val bodyweightInput = bodyweight ?: profile.bodyweightKg?.fromKg(unit)
        if (bodyweightInput == null) {
            return listOf("Enter bodyweight to log this lift.")
        }
        val calculation = PrCore.calculateEntry(
            weight = weight,
            unit = unit,
            sets = sets,
            reps = reps,
            bodyweight = bodyweightInput,
            sex = sex,
            previousBestEstimatedOneRmKg = previousBest,
        )
        if (calculation.errors.isNotEmpty()) return calculation.errors

        if (bodyweight != null && calculation.bodyweightKg != null) {
            db.profileDao().upsert(profile.copy(bodyweightKg = calculation.bodyweightKg))
        }
        db.entryDao().insert(
            LiftEntryEntity(
                liftId = liftId,
                performedAt = performedAt,
                weightKg = calculation.weightKg,
                originalUnit = unit.jsonValue,
                sets = sets,
                reps = reps,
                bodyweightKg = calculation.bodyweightKg,
                estimatedOneRmKg = calculation.estimatedOneRmKg,
                wilks = calculation.wilks,
                volumeKg = calculation.volumeKg,
                isPr = calculation.isPr,
                notes = notes.trim(),
            ),
        )
        return emptyList()
    }
}

data class AppState(
    val profile: ProfileEntity = ProfileEntity(),
    val lifts: List<LiftEntity> = emptyList(),
    val entries: List<EntryWithLift> = emptyList(),
)

private suspend fun AppDatabase.profileDaoSnapshot(): ProfileEntity {
    return profileDao().observeProfile().first() ?: ProfileEntity()
}

fun String.toSex(): Sex = if (this == Sex.Female.jsonValue) Sex.Female else Sex.Male
fun String.toWeightUnit(): WeightUnit = if (this == WeightUnit.Kg.jsonValue) WeightUnit.Kg else WeightUnit.Lb

fun Double.kgTo(unit: WeightUnit): Double = when (unit) {
    WeightUnit.Kg -> this
    WeightUnit.Lb -> this / 0.45359237
}

private fun Double.fromKg(unit: WeightUnit): Double = when (unit) {
    WeightUnit.Kg -> this
    WeightUnit.Lb -> this / 0.45359237
}
