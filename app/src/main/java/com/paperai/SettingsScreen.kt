// ==================== app/src/main/java/com/paperai/SettingsScreen.kt ====================
package com.paperai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val settings = vm.data.settings
    var editProvider by remember { mutableStateOf<Provider?>(null) }
    var editTemplate by remember { mutableStateOf<Template?>(null) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showSysPrompt by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            // ---- 服务商 ----
            SectionTitle("模型服务商")
            settings.providers.forEach { p ->
                val current = p.id == settings.providerId
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (current) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.saveSettings(
                                    settings.copy(
                                        providerId = p.id,
                                        model = p.models.firstOrNull() ?: settings.model
                                    )
                                )
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (current) "● " else "○ ") + p.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                p.baseUrl,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (p.apiKey.isBlank()) "未设置 Key" else "Key: " + "•".repeat(minOf(p.apiKey.length, 12)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        IconButton(onClick = { editProvider = p }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                        if (p.id != "custom" && p.id != "openai") {
                            IconButton(onClick = {
                                val rest = settings.providers.filterNot { it.id == p.id }
                                vm.saveSettings(
                                    settings.copy(
                                        providers = rest,
                                        providerId = if (current) (rest.firstOrNull()?.id ?: "") else settings.providerId
                                    )
                                )
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除")
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    editProvider = Provider(uid(), "新服务商", "https://", "", emptyList())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("添加服务商")
            }

            HorizontalDivider()

            // ---- 模型 ----
            SectionTitle("当前模型")
            Card(
                Modifier.fillMaxWidth().clickable { showModelPicker = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("模型", style = MaterialTheme.typography.labelSmall)
                        Text(
                            settings.model.ifBlank { "未选择" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
            }

            HorizontalDivider()

            // ---- 模板 ----
            SectionTitle("分析模板")
            Text(
                "开启后，每次发送会自动把模板内容作为系统提示词交给模型，实现「上传即分析」。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = settings.autoTemplate,
                    onCheckedChange = { vm.saveSettings(settings.copy(autoTemplate = it)) }
                )
                Spacer(Modifier.width(10.dp))
                Text("自动执行模板", style = MaterialTheme.typography.bodyMedium)
            }

            settings.templates.forEach { t ->
                val cur = t.id == settings.templateId
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (cur) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            vm.saveSettings(settings.copy(templateId = t.id))
                        }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (cur) "● " else "○ ") + t.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                t.content.replace("\n", " ").take(60) + "…",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { editTemplate = t }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = {
                            val rest = settings.templates.filterNot { it.id == t.id }
                            vm.saveSettings(
                                settings.copy(
                                    templates = rest,
                                    templateId = if (cur) (rest.firstOrNull()?.id ?: "") else settings.templateId
                                )
                            )
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    editTemplate = Template(uid(), "新模板", "")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("添加模板")
            }

            HorizontalDivider()

            // ---- 系统提示 ----
            SectionTitle("附加系统提示词")
            Card(
                Modifier.fillMaxWidth().clickable { showSysPrompt = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    settings.systemPrompt.ifBlank { "点击填写（会追加在模板之后）" },
                    Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider()

            // ---- 参数 ----
            SectionTitle("生成参数")
            Text("Temperature: ${"%.2f".format(settings.temperature)}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = settings.temperature.toFloat(),
                onValueChange = { vm.saveSettings(settings.copy(temperature = it.toDouble())) },
                valueRange = 0f..1.5f
            )
            Spacer(Modifier.height(4.dp))
            var maxTokensText by remember(settings.maxTokens) { mutableStateOf(settings.maxTokens.toString()) }
            OutlinedTextField(
                value = maxTokensText,
                onValueChange = {
                    maxTokensText = it.filter { c -> c.isDigit() }
                    maxTokensText.toIntOrNull()?.let { v ->
                        vm.saveSettings(settings.copy(maxTokens = v))
                    }
                },
                label = { Text("max_tokens") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(40.dp))
        }
    }

    // 服务商编辑
    editProvider?.let { p ->
        var name by remember(p.id) { mutableStateOf(p.name) }
        var url by remember(p.id) { mutableStateOf(p.baseUrl) }
        var key by remember(p.id) { mutableStateOf(p.apiKey) }
        var models by remember(p.id) { mutableStateOf(p.models.joinToString(", ")) }

        AlertDialog(
            onDismissRequest = { editProvider = null },
            title = { Text("编辑服务商") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true)
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Base URL，例如 https://api.openai.com/v1") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = models,
                        onValueChange = { models = it },
                        label = { Text("模型列表（英文逗号分隔）") },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val np = p.copy(
                        name = name.trim().ifBlank { "服务商" },
                        baseUrl = url.trim(),
                        apiKey = key.trim(),
                        models = models.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    )
                    val exists = settings.providers.any { it.id == np.id }
                    val list = if (exists) settings.providers.map { if (it.id == np.id) np else it }
                    else settings.providers + np
                    vm.saveSettings(
                        settings.copy(
                            providers = list,
                            providerId = if (settings.providerId.isBlank()) np.id else settings.providerId
                        )
                    )
                    editProvider = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editProvider = null }) { Text("取消") }
            }
        )
    }

    // 模板编辑
    editTemplate?.let { t ->
        var name by remember(t.id) { mutableStateOf(t.name) }
        var content by remember(t.id) { mutableStateOf(t.content) }

        AlertDialog(
            onDismissRequest = { editTemplate = null },
            title = { Text("编辑模板") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("模板名") }, singleLine = true)
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("模板内容（作为系统提示词）") },
                        minLines = 8,
                        maxLines = 14
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val nt = t.copy(name = name.trim().ifBlank { "模板" }, content = content)
                    val exists = settings.templates.any { it.id == nt.id }
                    val list = if (exists) settings.templates.map { if (it.id == nt.id) nt else it }
                    else settings.templates + nt
                    vm.saveSettings(
                        settings.copy(
                            templates = list,
                            templateId = if (settings.templateId.isBlank()) nt.id else settings.templateId
                        )
                    )
                    editTemplate = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editTemplate = null }) { Text("取消") }
            }
        )
    }

    // 模型选择
    if (showModelPicker) {
        val provider = vm.currentProvider()
        var custom by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("选择模型") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    (provider?.models ?: emptyList()).forEach { m ->
                        TextButton(
                            onClick = {
                                vm.saveSettings(settings.copy(model = m))
                                showModelPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                (if (m == settings.model) "✓ " else "") + m,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        label = { Text("手动输入模型名") },
                        singleLine = true
                    )
                    TextButton(
                        onClick = {
                            if (custom.isNotBlank()) {
                                vm.saveSettings(settings.copy(model = custom.trim()))
                                showModelPicker = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("使用该模型") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelPicker = false }) { Text("关闭") }
            }
        )
    }

    // 系统提示词
    if (showSysPrompt) {
        var text by remember { mutableStateOf(settings.systemPrompt) }
        AlertDialog(
            onDismissRequest = { showSysPrompt = false },
            title = { Text("附加系统提示词") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveSettings(settings.copy(systemPrompt = text))
                    showSysPrompt = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSysPrompt = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}
