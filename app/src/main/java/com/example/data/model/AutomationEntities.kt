package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "templates")
data class AutomationTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val isRunning: Boolean = false
)

@Entity(
    tableName = "actions",
    foreignKeys = [
        ForeignKey(
            entity = AutomationTemplate::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["templateId"])]
)
data class AutomationAction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val name: String,
    val sequenceOrder: Int,
    val cropImagePath: String?, // Absolute file path of the cropped target image
    val threshold: Double = 0.8,
    val timeoutSeconds: Int = 10,
    val actionType: String, // "TOQUE" or "ROLAGEM"
    val manualX: Int? = null,
    val manualY: Int? = null,
    val scrollDirection: String = "DOWN", // "DOWN", "UP", "LEFT", "RIGHT"
    val sourceScreenWidth: Int = 1080,
    val sourceScreenHeight: Int = 2400
)
