package com.example.autokitt.ml

data class BehaviorResult(
    val isAggressive: Boolean,
    val probability: Float,
    val reasons: List<String>,
    val explanationText: String,
    val jsonPayload: String
)

data class FaultResult(
    val status: String, // "Good", "Warning", "Faulty", "Requires Maintenance"
    val probability: Float,
    val possibleReason: String,
    val explanationText: String,
    val advice: String,
    val jsonPayload: String
)
