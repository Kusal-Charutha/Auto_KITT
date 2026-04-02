package com.example.autokitt.ml

data class VehicleDiagnosticResult(
    val status: String,
    val probability: Float,
    val possibleReason: String,
    val missingFeatures: List<String>,
    val timestamp: Long,
    val jsonPayload: String,
    val explanationText: String
)
