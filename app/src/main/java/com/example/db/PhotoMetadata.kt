package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_metadata")
data class PhotoMetadata(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val timestamp: Long = System.currentTimeMillis(),
    
    // Hardware settings during capture
    val resolutionMode: String, // "12.5MP (Binned)", "50MP (High Res)", "200MP (Ultra HD)", "RAW"
    val iso: Int,
    val shutterSpeed: String,
    val focusDistance: Float, // 0.0 for Auto, positive numbers for manual focus
    val exposureCompensation: Int,
    val rawCaptured: Boolean,
    val whiteBalanceKelvin: Int,
    
    // AI characteristics
    val aiSceneRecognized: String = "Unknown",
    val aiEnhancementsApplied: String = "" // Comma separated list of applied AI enhancements
)
