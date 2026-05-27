package com.example.data.database

import androidx.room.*
import com.example.data.model.AutomationAction
import com.example.data.model.AutomationTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationDao {
    @Query("SELECT * FROM templates ORDER BY id DESC")
    fun getAllTemplates(): Flow<List<AutomationTemplate>>

    @Query("SELECT * FROM templates WHERE id = :id")
    fun getTemplateById(id: Long): Flow<AutomationTemplate?>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getTemplateByIdSync(id: Long): AutomationTemplate?

    @Query("SELECT * FROM actions WHERE templateId = :templateId ORDER BY sequenceOrder ASC")
    fun getActionsForTemplate(templateId: Long): Flow<List<AutomationAction>>

    @Query("SELECT * FROM actions WHERE templateId = :templateId ORDER BY sequenceOrder ASC")
    suspend fun getActionsForTemplateSync(templateId: Long): List<AutomationAction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: AutomationTemplate): Long

    @Update
    suspend fun updateTemplate(template: AutomationTemplate)

    @Delete
    suspend fun deleteTemplate(template: AutomationTemplate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: AutomationAction): Long

    @Update
    suspend fun updateAction(action: AutomationAction)

    @Delete
    suspend fun deleteAction(action: AutomationAction)

    @Query("DELETE FROM actions WHERE templateId = :templateId")
    suspend fun deleteActionsForTemplate(templateId: Long)
}
