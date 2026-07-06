package com.prtracker.core

import org.json.JSONArray
import org.json.JSONObject

object PrCore {
    init {
        System.loadLibrary("prtracker_core")
    }

    external fun calculateEntryJson(input: String): String
    external fun majorTotalJson(input: String): String

    fun calculateEntry(
        weight: Double,
        unit: WeightUnit,
        sets: Int,
        reps: Int,
        bodyweight: Double?,
        sex: Sex?,
        previousBestEstimatedOneRmKg: Double?,
    ): EntryCalculation {
        val input = JSONObject()
            .put("weight", weight)
            .put("unit", unit.jsonValue)
            .put("sets", sets)
            .put("reps", reps)
            .put("bodyweight", bodyweight)
            .put("sex", sex?.jsonValue)
            .put("previousBestEstimatedOneRmKg", previousBestEstimatedOneRmKg)

        val output = JSONObject(calculateEntryJson(input.toString()))
        val errorsJson = output.optJSONArray("errors") ?: JSONArray()
        return EntryCalculation(
            weightKg = output.getDouble("weightKg"),
            estimatedOneRmKg = output.getDouble("estimatedOneRmKg"),
            bodyweightKg = output.optNullableDouble("bodyweightKg"),
            wilks = output.optNullableDouble("wilks"),
            isPr = output.getBoolean("isPr"),
            volumeKg = output.getDouble("volumeKg"),
            errors = List(errorsJson.length()) { index -> errorsJson.getString(index) },
        )
    }

    fun majorTotal(entries: List<MajorLiftBest>): MajorTotal {
        val input = JSONObject().put(
            "entries",
            JSONArray(entries.map {
                JSONObject()
                    .put("liftId", it.liftId)
                    .put("estimatedOneRmKg", it.estimatedOneRmKg)
            }),
        )
        val output = JSONObject(majorTotalJson(input.toString()))
        return MajorTotal(
            totalKg = output.getDouble("totalKg"),
            liftCount = output.getInt("liftCount"),
        )
    }
}

data class EntryCalculation(
    val weightKg: Double,
    val estimatedOneRmKg: Double,
    val bodyweightKg: Double?,
    val wilks: Double?,
    val isPr: Boolean,
    val volumeKg: Double,
    val errors: List<String>,
)

data class MajorLiftBest(val liftId: Long, val estimatedOneRmKg: Double)
data class MajorTotal(val totalKg: Double, val liftCount: Int)

enum class WeightUnit(val jsonValue: String, val label: String) {
    Kg("kg", "kg"),
    Lb("lb", "lb");
}

enum class Sex(val jsonValue: String, val label: String) {
    Male("male", "Male"),
    Female("female", "Female");
}

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (isNull(name)) null else optDouble(name)
