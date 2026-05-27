package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AutomationAction
import com.example.data.model.AutomationTemplate
import com.example.service.AutomationForegroundService
import com.example.service.ScreenAccessibilityService
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationDashboard(
    viewModel: AutomationViewModel,
    mediaProjectionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val actions by viewModel.actions.collectAsState()
    val selectedTemplate by viewModel.selectedTemplate.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "TERMINAL BOT",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    if (uiState !is AutomationViewModel.UiState.Dashboard) {
                        IconButton(onClick = {
                            when (uiState) {
                                is AutomationViewModel.UiState.AddAction -> {
                                    val tempId = (uiState as AutomationViewModel.UiState.AddAction).templateId
                                    viewModel.navigateTo(AutomationViewModel.UiState.TemplateDetail(tempId))
                                }
                                is AutomationViewModel.UiState.TemplateDetail -> {
                                    viewModel.selectTemplate(null)
                                    viewModel.navigateTo(AutomationViewModel.UiState.Dashboard)
                                }
                                else -> {
                                    viewModel.navigateTo(AutomationViewModel.UiState.Dashboard)
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "MainUiTransition"
            ) { targetState ->
                when (targetState) {
                    is AutomationViewModel.UiState.Dashboard -> {
                        DashboardScreen(
                            templates = templates,
                            onTemplateClick = { template ->
                                viewModel.selectTemplate(template)
                                viewModel.navigateTo(AutomationViewModel.UiState.TemplateDetail(template.id))
                            },
                            onAddTemplateClick = {
                                viewModel.navigateTo(AutomationViewModel.UiState.AddTemplate)
                            }
                        )
                    }
                    is AutomationViewModel.UiState.AddTemplate -> {
                        AddTemplateScreen(
                            onSave = { name, desc ->
                                viewModel.saveTemplate(name, desc)
                            },
                            onCancel = {
                                viewModel.navigateTo(AutomationViewModel.UiState.Dashboard)
                            }
                        )
                    }
                    is AutomationViewModel.UiState.TemplateDetail -> {
                        selectedTemplate?.let { template ->
                            TemplateDetailScreen(
                                template = template,
                                actions = actions,
                                onAddActionClick = {
                                    viewModel.navigateTo(AutomationViewModel.UiState.AddAction(template.id))
                                },
                                onDeleteTemplate = {
                                    viewModel.deleteTemplate(template)
                                },
                                onDeleteAction = { action ->
                                    viewModel.deleteAction(action)
                                },
                                onToggleService = { start ->
                                    if (start) {
                                        // Verify permissions first
                                        val isAccConnected = ScreenAccessibilityService.isServiceConnected
                                        val isOverlaysAllowed = Settings.canDrawOverlays(context)

                                        if (!isAccConnected) {
                                            Toast.makeText(
                                                context,
                                                "Ative primeiro o serviço de acessibilidade do App!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                            context.startActivity(intent)
                                        } else if (!isOverlaysAllowed) {
                                            Toast.makeText(
                                                context,
                                                "Permita a sobreposição de tela nas configurações!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            context.startActivity(intent)
                                        } else {
                                            // Request Media Projection capture permissions
                                            val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                            mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
                                        }
                                    } else {
                                        viewModel.stopAutomationService(context)
                                    }
                                }
                            )
                        } ?: run {
                            viewModel.navigateTo(AutomationViewModel.UiState.Dashboard)
                        }
                    }
                    is AutomationViewModel.UiState.AddAction -> {
                        AddActionScreen(
                            onSave = { name, actionType, uri, threshold, timeout, manualX, manualY, direction ->
                                viewModel.saveAction(
                                    templateId = targetState.templateId,
                                    name = name,
                                    actionType = actionType,
                                    cropUri = uri,
                                    threshold = threshold,
                                    timeoutSeconds = timeout,
                                    manualX = manualX,
                                    manualY = manualY,
                                    scrollDirection = direction
                                )
                            },
                            onCancel = {
                                viewModel.navigateTo(AutomationViewModel.UiState.TemplateDetail(targetState.templateId))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    templates: List<AutomationTemplate>,
    onTemplateClick: (AutomationTemplate) -> Unit,
    onAddTemplateClick: () -> Unit
) {
    val context = LocalContext.current
    var isAccConnected by remember { mutableStateOf(ScreenAccessibilityService.isServiceConnected) }
    var isOverlayActive by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    // Refresh permission statuses whenever user returns to view
    LaunchedEffect(Unit) {
        isAccConnected = ScreenAccessibilityService.isServiceConnected
        isOverlayActive = Settings.canDrawOverlays(context)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // Technical diagnostic dashboard
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "STATUS DO SISTEMA",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Accessibility Status row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isAccConnected) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isAccConnected) Color(0xFF34C759) else Color(0xFFFF3B30),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Serviço Acessibilidade:",
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp).testTag("accessibility_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isAccConnected) Color.Gray.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = if (isAccConnected) "OK" else "Ativar",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.08f))

                        // Overlay status row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isOverlayActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isOverlayActive) Color(0xFF34C759) else Color(0xFFFF3B30),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sobreposição de Tela:",
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp).testTag("overlay_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isOverlayActive) Color.Gray.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = if (isOverlayActive) "OK" else "Autorizar",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "TEMPLATES DE AUTOMAÇÃO",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (templates.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Nenhum template cadastrado.\nToca no botão '+' abaixo para criar um novo.",
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(templates) { template ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTemplateClick(template) }
                            .testTag("template_card_${template.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (template.isRunning) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = template.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    if (template.isRunning) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(Color(0xFF34C759), CircleShape)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = template.description,
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Abrir",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        FloatingActionButton(
            onClick = onAddTemplateClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_template_fab"),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.Black
        ) {
            Icon(Icons.Default.Add, contentDescription = "Novo Template")
        }
    }
}

@Composable
fun AddTemplateScreen(
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "CRIAR NOVO BOT",
            fontSize = 20.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nome do Automation") },
            placeholder = { Text("Ex: Bot do Tinder, Auto Swiper") },
            modifier = Modifier.fillMaxWidth().testTag("template_name_input"),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            )
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Descrição / Comandos") },
            placeholder = { Text("O que este fluxo irá automatizar?") },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .testTag("template_desc_input"),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("Cancelar")
            }

            Button(
                onClick = { onSave(name, description) },
                modifier = Modifier.weight(1f).height(48.dp).testTag("save_template_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Cadastrar", color = Color.Black)
            }
        }
    }
}

@Composable
fun TemplateDetailScreen(
    template: AutomationTemplate,
    actions: List<AutomationAction>,
    onAddActionClick: () -> Unit,
    onDeleteTemplate: () -> Unit,
    onDeleteAction: (AutomationAction) -> Unit,
    onToggleService: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Template Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = template.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = template.description,
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                    IconButton(onClick = onDeleteTemplate, modifier = Modifier.testTag("delete_template_btn")) {
                        Icon(Icons.Default.Delete, contentDescription = "Apagar", tint = Color.Red.copy(alpha = 0.8f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Engine start/stop master toggle control
                Button(
                    onClick = { onToggleService(!template.isRunning) },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("start_stop_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (template.isRunning) Color(0xFFFF3B30) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (template.isRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (template.isRunning) Color.White else Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (template.isRunning) "PARAR EXECUÇÃO DE GESTOS" else "INICIAR PROJEÇÃO E LOOP",
                        color = if (template.isRunning) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "PILHA DE AÇÕES (${actions.size})",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )

            TextButton(onClick = onAddActionClick, modifier = Modifier.testTag("add_action_text_btn")) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Adicionar Ação")
            }
        }

        if (actions.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhuma ação cadastrada.\nAdicione ações que buscam imagens e realizam toques.",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(actions) { action ->
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("action_card_${action.id}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Target crop preview
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val imagePath = action.cropImagePath
                                val bitmap = remember(imagePath) {
                                    imagePath?.let {
                                        val file = File(it)
                                        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                                    }
                                }

                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Target",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${action.sequenceOrder}. ${action.name}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Gesto: ${action.actionType} " +
                                            if (action.actionType == "TOQUE") {
                                                if (action.manualX != null) "(${action.manualX}, ${action.manualY})" else "(Automático)"
                                            } else {
                                                "(${action.scrollDirection})"
                                            },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = "Threshold: ${action.threshold} | Timeout: ${action.timeoutSeconds}s",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }

                            IconButton(onClick = { onDeleteAction(action) }, modifier = Modifier.testTag("delete_action_btn_${action.id}")) {
                                Icon(Icons.Default.Delete, contentDescription = "Remover", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddActionScreen(
    onSave: (String, String, Uri?, Double, Int, Int?, Int?, String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var actionType by remember { mutableStateOf("TOQUE") } // "TOQUE" or "ROLAGEM"
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var threshold by remember { mutableStateOf(0.8) }
    var timeout by remember { mutableStateOf(10) }
    var manualXStr by remember { mutableStateOf("") }
    var manualYStr by remember { mutableStateOf("") }
    var scrollDirection by remember { mutableStateOf("DOWN") }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedUri = uri
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "ADICIONAR GESTO GATILHO",
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome da Ação") },
                placeholder = { Text("Ex: Clicar no botão 'Próximo'") },
                modifier = Modifier.fillMaxWidth().testTag("action_name_input"),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
        }

        // Action Type selectors
        item {
            Column {
                Text(text = "Tipo de Gesto:", fontSize = 14.sp, color = Color.Gray)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { actionType = "TOQUE" }
                    ) {
                        RadioButton(
                            selected = actionType == "TOQUE",
                            onClick = { actionType = "TOQUE" },
                            modifier = Modifier.testTag("radio_toque")
                        )
                        Text("Toque físico")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { actionType = "ROLAGEM" }
                    ) {
                        RadioButton(
                            selected = actionType == "ROLAGEM",
                            onClick = { actionType = "ROLAGEM" },
                            modifier = Modifier.testTag("radio_rolagem")
                        )
                        Text("Rolagem (Scroll)")
                    }
                }
            }
        }

        // Conditionally show custom coordinates or direction config
        if (actionType == "TOQUE") {
            item {
                Column {
                    Text(text = "Coordenadas Manuais (Opcional):", fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Se mantidos vazios, o OpenCV calculará as coordenadas automaticamente de acordo com o centro da imagem encontrada.",
                        fontSize = 12.sp,
                        color = Color.LightGray.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = manualXStr,
                            onValueChange = { manualXStr = it },
                            label = { Text("Valor X (px)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("manual_x_input"),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                        OutlinedTextField(
                            value = manualYStr,
                            onValueChange = { manualYStr = it },
                            label = { Text("Valor Y (px)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("manual_y_input"),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        } else {
            // Scroll directions dropdown simulation
            item {
                Column {
                    Text(text = "Direção da Rolagem:", fontSize = 14.sp, color = Color.Gray)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf("DOWN", "UP", "LEFT", "RIGHT").forEach { dir ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (scrollDirection == dir) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .border(
                                        1.dp,
                                        if (scrollDirection == dir) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { scrollDirection = dir }
                                    .padding(vertical = 10.dp)
                                    .testTag("dir_btn_$dir"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (dir) {
                                        "DOWN" -> "Baixo"
                                        "UP" -> "Cima"
                                        "LEFT" -> "Esq"
                                        "RIGHT" -> "Dir"
                                        else -> dir
                                    },
                                    color = if (scrollDirection == dir) MaterialTheme.colorScheme.primary else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Image Crop Selector
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Imagem de Referência (Subimagem Crop):", fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .clickable { pickerLauncher.launch("image/*") }
                        .testTag("image_picker_box"),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedUri != null) {
                        val context = LocalContext.current
                        val bitmap = remember(selectedUri) {
                            try {
                                val stream = context.contentResolver.openInputStream(selectedUri!!)
                                BitmapFactory.decodeStream(stream)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Selected match image",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(8.dp)
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddCircle, contentDescription = null, size = 32.dp, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Selecionar Imagem do Crop", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }

        // Threshold configuration
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Limiar de Confiança (Threshold):", fontSize = 14.sp, color = Color.Gray)
                    Text("%.2f".format(threshold), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = threshold.toFloat(),
                    onValueChange = { threshold = it.toDouble() },
                    valueRange = 0.1f..1.0f,
                    modifier = Modifier.testTag("threshold_slider")
                )
            }
        }

        // Timeout configuration
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tempo limite de busca (Timeout):", fontSize = 14.sp, color = Color.Gray)
                    Text("$timeout segundos", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = timeout.toFloat(),
                    onValueChange = { timeout = it.toInt() },
                    valueRange = 2f..60f,
                    steps = 58,
                    modifier = Modifier.testTag("timeout_slider")
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Cancelar")
                }

                Button(
                    onClick = {
                        val manualX = manualXStr.toIntOrNull()
                        val manualY = manualYStr.toIntOrNull()
                        onSave(name, actionType, selectedUri, threshold, timeout, manualX, manualY, scrollDirection)
                    },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("save_action_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Salvar Gesto", color = Color.Black)
                }
            }
        }
    }
}

@Composable
private fun Icon(imageVector: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String?, size: androidx.compose.ui.unit.Dp, tint: Color) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(size)
    )
}
