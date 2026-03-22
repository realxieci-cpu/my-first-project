package com.example.aibookkeeper

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val screenshotCallback = Activity.ScreenCaptureCallback {
        appState?.onScreenshotDetected()
    }

    private var appState: AppStateHolder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            appState = rememberAppStateHolder()
            AiBookkeeperTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AiBookkeeperApp(appState = appState!!)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerScreenshotDetection()
        }
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            unregisterScreenCaptureCallback(screenshotCallback)
        }
        super.onStop()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun registerScreenshotDetection() {
        registerScreenCaptureCallback(mainExecutor, screenshotCallback)
    }
}

data class BillRecord(
    val id: String,
    val merchant: String,
    val amount: String,
    val category: String,
    val happenedAt: String,
    val paymentMethod: String,
    val note: String,
    val source: String,
)

data class DraftBill(
    val merchant: String = "",
    val amount: String = "",
    val category: String = "",
    val happenedAt: String = "",
    val paymentMethod: String = "",
    val note: String = "",
    val source: String = "截图账单",
)

class AppStateHolder {
    var showScreenshotDialog by mutableStateOf(false)
    var showSaveDialog by mutableStateOf(false)
    var statusMessage by mutableStateOf("等待截图事件。检测到截图后，会提示用户是否进入 AI 自动记账流程。")
    var selectedImageUri by mutableStateOf<Uri?>(null)
    var importedImageLabel by mutableStateOf("还没有导入截图")
    var currentDraft by mutableStateOf(
        DraftBill(
            merchant = "星巴克",
            amount = "36.00",
            category = "餐饮",
            happenedAt = nowString(),
            paymentMethod = "微信支付",
            note = "演示识别结果：拿铁 + 三明治",
        )
    )
    val savedRecords = mutableStateListOf<BillRecord>()

    fun onScreenshotDetected() {
        showScreenshotDialog = true
        statusMessage = "检测到截图：请决定是否进入 AI 记账流程。"
    }

    fun dismissScreenshotDialog() {
        showScreenshotDialog = false
        statusMessage = "已忽略本次截图，不进入记账流程。"
    }

    fun enterRecognitionFlow() {
        showScreenshotDialog = false
        statusMessage = "请导入刚刚截图的账单图片，AI 会自动整理账单字段。"
    }

    fun importScreenshot(uri: Uri?) {
        selectedImageUri = uri
        importedImageLabel = uri?.toString() ?: "未选择图片"
        if (uri != null) {
            currentDraft = currentDraft.copy(
                source = "截图导入",
                note = "已导入截图：${uri.lastPathSegment ?: "screen_capture"}，可继续修正识别结果。"
            )
            statusMessage = "截图已导入，已生成一份 AI 识别草稿。请检查后选择是否保存。"
        }
    }

    fun runAiRecognition() {
        currentDraft = generateDemoDraft(currentDraft, importedImageLabel)
        showSaveDialog = true
        statusMessage = "AI 已完成账单整理，请确认是否保存这一次记账信息。"
    }

    fun updateDraft(transform: DraftBill.() -> DraftBill) {
        currentDraft = currentDraft.transform()
    }

    fun dismissSaveDialog() {
        showSaveDialog = false
        statusMessage = "你可以继续修改识别结果，稍后再保存。"
    }

    fun saveDraft() {
        savedRecords.add(
            0,
            BillRecord(
                id = System.currentTimeMillis().toString(),
                merchant = currentDraft.merchant,
                amount = currentDraft.amount,
                category = currentDraft.category,
                happenedAt = currentDraft.happenedAt,
                paymentMethod = currentDraft.paymentMethod,
                note = currentDraft.note,
                source = currentDraft.source,
            )
        )
        showSaveDialog = false
        statusMessage = "已保存 1 条新的记账信息。"
    }

    companion object {
        private fun generateDemoDraft(base: DraftBill, imageLabel: String): DraftBill {
            val category = when {
                imageLabel.contains("滴滴", ignoreCase = true) -> "出行"
                imageLabel.contains("美团", ignoreCase = true) -> "餐饮"
                imageLabel.contains("京东", ignoreCase = true) -> "购物"
                else -> base.category.ifBlank { "日常支出" }
            }
            val merchant = when {
                imageLabel.contains("滴滴", ignoreCase = true) -> "滴滴出行"
                imageLabel.contains("美团", ignoreCase = true) -> "美团"
                imageLabel.contains("京东", ignoreCase = true) -> "京东"
                else -> base.merchant.ifBlank { "待确认商户" }
            }
            val amount = base.amount.ifBlank { "28.50" }
            val paymentMethod = base.paymentMethod.ifBlank { "支付宝" }
            return base.copy(
                merchant = merchant,
                amount = amount,
                category = category,
                happenedAt = base.happenedAt.ifBlank { nowString() },
                paymentMethod = paymentMethod,
                note = "AI 已根据截图内容生成账单草稿：$imageLabel",
                source = "AI截图识别",
            )
        }
    }
}

@Composable
private fun rememberAppStateHolder(): AppStateHolder = remember { AppStateHolder() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiBookkeeperApp(appState: AppStateHolder) {
    val pickMediaLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        appState.importScreenshot(uri)
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFF6F8FF), Color(0xFFE7EEFF))
                    )
                )
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HeroSection(statusMessage = appState.statusMessage)
            }
            item {
                ScreenshotFlowCard(
                    onSimulateShot = appState::onScreenshotDetected,
                    onPickImage = {
                        appState.enterRecognitionFlow()
                        pickMediaLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    importedImageLabel = appState.importedImageLabel,
                )
            }
            item {
                DraftEditor(
                    draft = appState.currentDraft,
                    onMerchantChange = { appState.updateDraft { copy(merchant = it) } },
                    onAmountChange = { appState.updateDraft { copy(amount = it) } },
                    onCategoryChange = { appState.updateDraft { copy(category = it) } },
                    onTimeChange = { appState.updateDraft { copy(happenedAt = it) } },
                    onPaymentMethodChange = { appState.updateDraft { copy(paymentMethod = it) } },
                    onNoteChange = { appState.updateDraft { copy(note = it) } },
                    onAiRecognize = appState::runAiRecognition,
                )
            }
            item {
                SavedRecordsSection(records = appState.savedRecords)
            }
        }
    }

    if (appState.showScreenshotDialog) {
        AlertDialog(
            onDismissRequest = appState::dismissScreenshotDialog,
            title = { Text("检测到你刚刚截图") },
            text = {
                Text("是否立即进入 AI记账软件，对这张截图进行账单自动识别、字段整理，并决定是否保存一次记账信息？")
            },
            confirmButton = {
                Button(onClick = appState::enterRecognitionFlow) {
                    Text("进入 App 处理")
                }
            },
            dismissButton = {
                TextButton(onClick = appState::dismissScreenshotDialog) {
                    Text("暂不处理")
                }
            }
        )
    }

    if (appState.showSaveDialog) {
        AlertDialog(
            onDismissRequest = appState::dismissSaveDialog,
            title = { Text("是否保存这次记账？") },
            text = {
                Text(
                    "AI 已整理出本次账单：${appState.currentDraft.merchant} / ${appState.currentDraft.amount} 元 / ${appState.currentDraft.category}。" +
                        "\n确认后将存入本地账单列表。"
                )
            },
            confirmButton = {
                Button(onClick = appState::saveDraft) {
                    Text("确认保存")
                }
            },
            dismissButton = {
                TextButton(onClick = appState::dismissSaveDialog) {
                    Text("再检查一下")
                }
            }
        )
    }
}

@Composable
private fun HeroSection(statusMessage: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF162448))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("AI记账软件", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "面向安卓手机的截图驱动记账流程：截图 → 弹窗提醒 → 进入 App → AI 整理账单 → 确认保存。",
                color = Color(0xFFD8E2FF),
                lineHeight = 22.sp
            )
            StatusChip(statusMessage)
        }
    }
}

@Composable
private fun StatusChip(message: String) {
    Surface(color = Color(0x332AB3FF), shape = RoundedCornerShape(999.dp)) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color.White,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ScreenshotFlowCard(
    onSimulateShot: () -> Unit,
    onPickImage: () -> Unit,
    importedImageLabel: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = Color(0xFF295EF3))
                Column {
                    Text("截图触发流程", fontWeight = FontWeight.SemiBold)
                    Text("Android 14+ 可接入官方截图检测回调；开发阶段可使用模拟按钮演示。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onSimulateShot) {
                    Text("模拟截图事件")
                }
                OutlinedButton(onClick = onPickImage) {
                    Text("导入刚截图的账单")
                }
            }
            Divider()
            InfoRow(Icons.Outlined.Collections, "最近导入截图", importedImageLabel)
            InfoRow(Icons.Outlined.Schedule, "建议流程", "截图后选择“进入 App 处理”，然后自动整理并确认保存")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftEditor(
    draft: DraftBill,
    onMerchantChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onPaymentMethodChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onAiRecognize: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color(0xFF7C3AED))
                Column {
                    Text("AI 自动识别账单信息", fontWeight = FontWeight.SemiBold)
                    Text("你可以先导入截图，再点击下方按钮自动生成账单草稿；之后仍可手动修正。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag("自动识别金额")
                Tag("自动识别商户")
                Tag("自动归类")
                Tag("确认后保存一次")
            }

            OutlinedTextField(value = draft.merchant, onValueChange = onMerchantChange, modifier = Modifier.fillMaxWidth(), label = { Text("商户名称") })
            OutlinedTextField(value = draft.amount, onValueChange = onAmountChange, modifier = Modifier.fillMaxWidth(), label = { Text("金额") })
            OutlinedTextField(value = draft.category, onValueChange = onCategoryChange, modifier = Modifier.fillMaxWidth(), label = { Text("分类") })
            OutlinedTextField(value = draft.happenedAt, onValueChange = onTimeChange, modifier = Modifier.fillMaxWidth(), label = { Text("发生时间") })
            OutlinedTextField(value = draft.paymentMethod, onValueChange = onPaymentMethodChange, modifier = Modifier.fillMaxWidth(), label = { Text("支付方式") })
            OutlinedTextField(value = draft.note, onValueChange = onNoteChange, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("备注 / AI说明") })

            Button(onClick = onAiRecognize, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.ReceiptLong, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("开始 AI 识别并整理账单")
            }
        }
    }
}

@Composable
private fun SavedRecordsSection(records: List<BillRecord>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Save, contentDescription = null, tint = Color(0xFF0F9D58))
                Column {
                    Text("已保存账单", fontWeight = FontWeight.SemiBold)
                    Text("用户在最后一步确认后，单次记账会进入这里。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("还没有保存任何账单。先截图、识别，再确认保存一次记账吧。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                records.forEach { record ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F7FF))) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${record.merchant} · ${record.amount} 元", fontWeight = FontWeight.Bold)
                            Text("分类：${record.category}    时间：${record.happenedAt}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("支付方式：${record.paymentMethod}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("来源：${record.source}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(record.note, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color(0xFF295EF3))
        Column {
            Text(title, fontWeight = FontWeight.Medium)
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(color = Color(0xFFECE7FF), shape = RoundedCornerShape(999.dp)) {
        Text(text = text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Color(0xFF5E35B1), fontSize = 12.sp)
    }
}

@Composable
private fun AiBookkeeperTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

private fun nowString(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
