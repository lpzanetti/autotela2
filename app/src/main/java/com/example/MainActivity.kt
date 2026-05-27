package com.example

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.example.service.AutomationForegroundService
import com.example.ui.AutomationDashboard
import com.example.ui.AutomationViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AutomationViewModel

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val templateId = viewModel.selectedTemplate.value?.id ?: -1L
            if (templateId != -1L) {
                val serviceIntent = Intent(this, AutomationForegroundService::class.java).apply {
                    action = AutomationForegroundService.ACTION_START
                    putExtra(AutomationForegroundService.EXTRA_TEMPLATE_ID, templateId)
                    putExtra(AutomationForegroundService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(AutomationForegroundService.EXTRA_RESULT_DATA, result.data)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                Toast.makeText(this, "Motor de automação iniciado em segundo plano!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Nenhum template selecionado para execução.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Consentimento de captura de tela negado pelo usuário.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Instantiate core ViewModel
        viewModel = ViewModelProvider(this)[AutomationViewModel::class.java]

        setContent {
            MyApplicationTheme {
                AutomationDashboard(
                    viewModel = viewModel,
                    mediaProjectionLauncher = mediaProjectionLauncher
                )
            }
        }
    }
}
