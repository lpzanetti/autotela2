package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AutomationDatabase
import com.example.data.model.AutomationAction
import com.example.data.model.AutomationTemplate
import com.example.data.repository.AutomationRepository
import com.example.service.AutomationForegroundService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class AutomationViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AutomationDatabase.getInstance(application)
    private val repository = AutomationRepository(db.dao)

    val templates: StateFlow<List<AutomationTemplate>> = repository.allTemplates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTemplate = MutableStateFlow<AutomationTemplate?>(null)
    val selectedTemplate: StateFlow<AutomationTemplate?> = _selectedTemplate.asStateFlow()

    private val _actions = MutableStateFlow<List<AutomationAction>>(emptyList())
    val actions: StateFlow<List<AutomationAction>> = _actions.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Dashboard)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Create a dummy template on first run if database is empty
    init {
        viewModelScope.launch {
            repository.allTemplates.first().let { currentList ->
                if (currentList.isEmpty()) {
                    createSampleTemplate()
                }
            }
        }
    }

    fun selectTemplate(template: AutomationTemplate?) {
        _selectedTemplate.value = template
        if (template != null) {
            viewModelScope.launch {
                repository.getActionsForTemplate(template.id).collect {
                    _actions.value = it
                }
            }
        } else {
            _actions.value = emptyList()
        }
    }

    fun navigateTo(state: UiState) {
        _uiState.value = state
    }

    fun saveTemplate(name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val template = AutomationTemplate(name = name, description = description)
            repository.insertTemplate(template)
            _uiState.value = UiState.Dashboard
        }
    }

    fun deleteTemplate(template: AutomationTemplate) {
        viewModelScope.launch {
            repository.deleteTemplate(template)
            if (_selectedTemplate.value?.id == template.id) {
                selectTemplate(null)
            }
            _uiState.value = UiState.Dashboard
        }
    }

    fun saveAction(
        templateId: Long,
        name: String,
        actionType: String,
        cropUri: Uri?,
        threshold: Double,
        timeoutSeconds: Int,
        manualX: Int?,
        manualY: Int?,
        scrollDirection: String
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            var savedCropPath: String? = null
            cropUri?.let { uri ->
                savedCropPath = copyUriToInternalStorage(uri)
            }

            val count = _actions.value.size
            val action = AutomationAction(
                templateId = templateId,
                name = name,
                sequenceOrder = count + 1,
                cropImagePath = savedCropPath,
                threshold = threshold,
                timeoutSeconds = timeoutSeconds,
                actionType = actionType,
                manualX = manualX,
                manualY = manualY,
                scrollDirection = scrollDirection
            )
            repository.insertAction(action)
            _uiState.value = UiState.TemplateDetail(templateId)
        }
    }

    fun deleteAction(action: AutomationAction) {
        viewModelScope.launch {
            repository.deleteAction(action)
        }
    }

    private fun copyUriToInternalStorage(uri: Uri): String? {
        val context = getApplication<Application>()
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val file = File(context.cacheDir, "crop_${System.currentTimeMillis()}.png")
            val outputStream = FileOutputStream(file)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("ViewModel", "Failed to save selected crop image to local storage", e)
            null
        }
    }

    private suspend fun createSampleTemplate() {
        val templateId = repository.insertTemplate(
            AutomationTemplate(
                name = "Automação Simulada (Dummie)",
                description = "Busca por um círculo vermelho na tela e realiza TOQUE ou ROLAGEM."
            )
        )

        // Generate a sample red dot crop in internal storage for testing out-of-the-box
        val context = getApplication<Application>()
        val file = File(context.cacheDir, "sample_red_dot.png")
        if (!file.exists()) {
            try {
                val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                val paint = android.graphics.Paint().apply {
                    color = 0xFFFF0000.toInt()
                    style = android.graphics.Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawCircle(40f, 40f, 30f, paint)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bitmap.recycle()
            } catch (e: Exception) {
                Log.e("ViewModel", "Failed to create dummie sample crop", e)
            }
        }

        repository.insertAction(
            AutomationAction(
                templateId = templateId,
                name = "Ação 1: Toque no Botão Vermelho",
                sequenceOrder = 1,
                cropImagePath = file.absolutePath,
                threshold = 0.75,
                timeoutSeconds = 12,
                actionType = "TOQUE"
            )
        )

        repository.insertAction(
            AutomationAction(
                templateId = templateId,
                name = "Ação 2: Rolar para baixo",
                sequenceOrder = 2,
                cropImagePath = file.absolutePath,
                threshold = 0.70,
                timeoutSeconds = 10,
                actionType = "ROLAGEM",
                scrollDirection = "DOWN"
            )
        )
    }

    fun stopAutomationService(context: Context) {
        val intent = Intent(context, AutomationForegroundService::class.java).apply {
            action = AutomationForegroundService.ACTION_STOP
        }
        context.stopService(intent)
    }

    sealed interface UiState {
        object Dashboard : UiState
        object AddTemplate : UiState
        data class TemplateDetail(val templateId: Long) : UiState
        data class AddAction(val templateId: Long) : UiState
    }
}
