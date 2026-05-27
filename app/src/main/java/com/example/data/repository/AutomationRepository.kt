package com.example.data.repository

import com.example.data.database.AutomationDao
import com.example.data.model.AutomationAction
import com.example.data.model.AutomationTemplate
import kotlinx.coroutines.flow.Flow

class AutomationRepository(private val dao: AutomationDao) {
    val allTemplates: Flow<List<AutomationTemplate>> = dao.getAllTemplates()

    fun getTemplateById(id: Long): Flow<AutomationTemplate?> = dao.getTemplateById(id)

    suspend fun getTemplateByIdSync(id: Long): AutomationTemplate? = dao.getTemplateByIdSync(id)

    fun getActionsForTemplate(templateId: Long): Flow<List<AutomationAction>> =
        dao.getActionsForTemplate(templateId)

    suspend fun getActionsForTemplateSync(templateId: Long): List<AutomationAction> =
        dao.getActionsForTemplateSync(templateId)

    suspend fun insertTemplate(template: AutomationTemplate): Long = dao.insertTemplate(template)

    suspend fun updateTemplate(template: AutomationTemplate) = dao.updateTemplate(template)

    suspend fun deleteTemplate(template: AutomationTemplate) = dao.deleteTemplate(template)

    suspend fun insertAction(action: AutomationAction): Long = dao.insertAction(action)

    suspend fun updateAction(action: AutomationAction) = dao.updateAction(action)

    suspend fun deleteAction(action: AutomationAction) = dao.deleteAction(action)

    suspend fun deleteActionsForTemplate(templateId: Long) = dao.deleteActionsForTemplate(templateId)
}
