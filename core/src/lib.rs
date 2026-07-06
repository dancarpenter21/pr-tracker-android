use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use serde::{Deserialize, Serialize};

const LB_TO_KG: f64 = 0.453_592_37;

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct EntryInput {
    weight: f64,
    unit: Unit,
    sets: u32,
    reps: u32,
    bodyweight: Option<f64>,
    sex: Option<Sex>,
    previous_best_estimated_one_rm_kg: Option<f64>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct EntryOutput {
    weight_kg: f64,
    estimated_one_rm_kg: f64,
    bodyweight_kg: Option<f64>,
    wilks: Option<f64>,
    is_pr: bool,
    volume_kg: f64,
    errors: Vec<String>,
}

#[derive(Clone, Copy, Debug, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Unit {
    Kg,
    Lb,
}

#[derive(Clone, Copy, Debug, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Sex {
    Male,
    Female,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct MajorTotalInput {
    entries: Vec<MajorLiftBest>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct MajorLiftBest {
    lift_id: i64,
    estimated_one_rm_kg: f64,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct MajorTotalOutput {
    total_kg: f64,
    lift_count: usize,
}

pub fn normalize_weight(value: f64, unit: Unit) -> f64 {
    match unit {
        Unit::Kg => value,
        Unit::Lb => value * LB_TO_KG,
    }
}

pub fn epley_one_rm(weight_kg: f64, reps: u32) -> f64 {
    if reps <= 1 {
        weight_kg
    } else {
        weight_kg * (1.0 + reps as f64 / 30.0)
    }
}

pub fn wilks_score(total_kg: f64, bodyweight_kg: f64, sex: Sex) -> Option<f64> {
    if total_kg <= 0.0 || bodyweight_kg <= 0.0 {
        return None;
    }

    let coefficients = match sex {
        Sex::Male => [-216.047_514_4, 16.260_633_9, -0.002_388_645, -0.001_137_32, 7.018_63e-6, -1.291e-8],
        Sex::Female => [594.317_477_755_82, -27.238_425_364_47, 0.821_122_268_71, -0.009_307_339_13, 4.731_582e-5, -9.054e-8],
    };

    let x = bodyweight_kg;
    let denominator = coefficients[0]
        + coefficients[1] * x
        + coefficients[2] * x.powi(2)
        + coefficients[3] * x.powi(3)
        + coefficients[4] * x.powi(4)
        + coefficients[5] * x.powi(5);

    if denominator <= 0.0 {
        None
    } else {
        Some(total_kg * 500.0 / denominator)
    }
}

fn calculate_entry(input: EntryInput) -> EntryOutput {
    let mut errors = Vec::new();
    if input.weight <= 0.0 {
        errors.push("Weight must be greater than zero.".to_string());
    }
    if input.sets == 0 {
        errors.push("Sets must be at least one.".to_string());
    }
    if input.reps == 0 {
        errors.push("Reps must be at least one.".to_string());
    }

    let weight_kg = normalize_weight(input.weight.max(0.0), input.unit);
    let bodyweight_kg = input.bodyweight.map(|value| normalize_weight(value.max(0.0), input.unit));
    let estimated_one_rm_kg = if errors.is_empty() {
        epley_one_rm(weight_kg, input.reps)
    } else {
        0.0
    };
    let wilks = match (bodyweight_kg, input.sex) {
        (Some(bodyweight), Some(sex)) => wilks_score(estimated_one_rm_kg, bodyweight, sex),
        _ => None,
    };
    let is_pr = input
        .previous_best_estimated_one_rm_kg
        .map(|best| estimated_one_rm_kg > best)
        .unwrap_or(true);

    EntryOutput {
        weight_kg,
        estimated_one_rm_kg,
        bodyweight_kg,
        wilks,
        is_pr,
        volume_kg: weight_kg * input.sets as f64 * input.reps as f64,
        errors,
    }
}

fn major_total(input: MajorTotalInput) -> MajorTotalOutput {
    let mut bests: std::collections::HashMap<i64, f64> = std::collections::HashMap::new();
    for entry in input.entries {
        bests
            .entry(entry.lift_id)
            .and_modify(|best| *best = best.max(entry.estimated_one_rm_kg))
            .or_insert(entry.estimated_one_rm_kg);
    }

    MajorTotalOutput {
        total_kg: bests.values().sum(),
        lift_count: bests.len(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_prtracker_core_PrCore_calculateEntryJson(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
) -> jstring {
    let output = env
        .get_string(&input)
        .map(|value| value.to_string_lossy().into_owned())
        .and_then(|json| {
            serde_json::from_str::<EntryInput>(&json)
                .map(calculate_entry)
                .map_err(|_err| jni::errors::Error::JavaException)
                .and_then(|entry| {
                    serde_json::to_string(&entry).map_err(|_err| jni::errors::Error::JavaException)
                })
        })
        .unwrap_or_else(|_| {
            serde_json::json!({
                "weightKg": 0.0,
                "estimatedOneRmKg": 0.0,
                "bodyweightKg": null,
                "wilks": null,
                "isPr": false,
                "volumeKg": 0.0,
                "errors": ["Unable to calculate entry."]
            })
            .to_string()
        });

    env.new_string(output)
        .expect("failed to allocate JNI string")
        .into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_prtracker_core_PrCore_majorTotalJson(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
) -> jstring {
    let output = env
        .get_string(&input)
        .map(|value| value.to_string_lossy().into_owned())
        .ok()
        .and_then(|json| serde_json::from_str::<MajorTotalInput>(&json).ok())
        .map(major_total)
        .and_then(|total| serde_json::to_string(&total).ok())
        .unwrap_or_else(|| "{\"totalKg\":0.0,\"liftCount\":0}".to_string());

    env.new_string(output)
        .expect("failed to allocate JNI string")
        .into_raw()
}

#[cfg(test)]
mod tests {
    use super::*;
    use approx::assert_relative_eq;

    #[test]
    fn converts_pounds_to_kg() {
        assert_relative_eq!(normalize_weight(225.0, Unit::Lb), 102.058, epsilon = 0.001);
    }

    #[test]
    fn calculates_epley_one_rm() {
        assert_relative_eq!(epley_one_rm(100.0, 5), 116.666, epsilon = 0.001);
    }

    #[test]
    fn calculates_wilks_for_male() {
        let score = wilks_score(200.0, 90.0, Sex::Male).unwrap();
        assert!(score > 120.0);
    }

    #[test]
    fn detects_pr() {
        let result = calculate_entry(EntryInput {
            weight: 225.0,
            unit: Unit::Lb,
            sets: 3,
            reps: 5,
            bodyweight: Some(180.0),
            sex: Some(Sex::Male),
            previous_best_estimated_one_rm_kg: Some(100.0),
        });
        assert!(result.is_pr);
        assert!(result.errors.is_empty());
    }
}
