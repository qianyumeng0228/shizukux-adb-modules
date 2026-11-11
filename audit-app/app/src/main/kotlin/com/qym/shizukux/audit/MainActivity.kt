package com.qym.shizukux.audit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AuditColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF0B3D38),
    secondary = Color(0xFF475569),
    background = Color(0xFFF6F7F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDF1F4),
    error = Color(0xFFB42318)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = AuditColors) {
                AuditApp()
            }
        }
    }
}

@Composable
private fun AuditApp() {
    val vm: AuditViewModel = viewModel()
    var tab by remember { mutableStateOf(0) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
            Text(
                "ShizuX 模块审核",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                vm.endpoint,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
                fontFamily = FontFamily.Monospace
            )
        }

        PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            listOf("待审", "已上架", "设置").forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
            }
        }

        if (vm.message != null || vm.error != null) {
            NoticeBar(
                text = vm.error ?: vm.message.orEmpty(),
                isError = vm.error != null,
                onDismiss = { vm.dismissNotice() }
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> PendingTab(vm)
                1 -> PublishedTab(vm)
                else -> SettingsTab(vm)
            }
        }
    }
}

@Composable
private fun NoticeBar(text: String, isError: Boolean, onDismiss: () -> Unit) {
    val background = if (isError) Color(0xFFFDECEA) else Color(0xFFE7F6F3)
    val foreground = if (isError) MaterialTheme.colorScheme.error else Color(0xFF0B3D38)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .background(background, RoundedCornerShape(10.dp))
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = foreground, fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text("关闭", fontSize = 12.sp, color = foreground) }
    }
}

@Composable
private fun PendingTab(vm: AuditViewModel) {
    var approveTarget by remember { mutableStateOf<Submission?>(null) }
    var rejectTarget by remember { mutableStateOf<Submission?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "待审 ${vm.pending.size} 项",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            if (vm.loadingPending) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            TextButton(onClick = { vm.refreshPending() }) { Text("刷新") }
        }

        if (vm.pending.isEmpty()) {
            EmptyHint(if (vm.loadingPending) "正在读取队列…" else "队列为空，暂时没有待审模块")
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
            ) {
                items(vm.pending, key = { it.ticket }) { item ->
                    SubmissionCard(
                        item = item,
                        busy = vm.busyTicket == item.ticket,
                        onApprove = { approveTarget = item },
                        onReject = { rejectTarget = item }
                    )
                }
            }
        }
    }

    approveTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { approveTarget = null },
            title = { Text("通过并发布？") },
            text = {
                Text("${target.displayName} v${target.version} 将通过校验并写入模块仓库，随后自动关闭 issue #${target.issueNumber ?: 0}。")
            },
            confirmButton = {
                TextButton(onClick = {
                    approveTarget = null
                    vm.approve(target)
                }) { Text("通过") }
            },
            dismissButton = { TextButton(onClick = { approveTarget = null }) { Text("取消") } }
        )
    }

    rejectTarget?.let { target ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text("驳回 ${target.displayName}") },
            text = {
                Column {
                    Text("原因会写进队列记录并评论到 issue。", fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("驳回原因") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    rejectTarget = null
                    vm.reject(target, reason.ifBlank { "未通过审核" })
                }) { Text("驳回", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { rejectTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun SubmissionCard(
    item: Submission,
    busy: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
                Badge("v${item.version}")
                Spacer(Modifier.weight(1f))
                Text(formatSize(item.sizeBytes), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.height(4.dp))
            Text(item.id, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.secondary)
            item.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary, maxLines = 3)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "SHA-256 ${item.sha256.take(16)}…  ·  ${formatTime(item.submittedAt)}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.secondary
            )
            if (item.issueNumber != null) {
                Spacer(Modifier.height(2.dp))
                Text("issue #${item.issueNumber}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onReject, enabled = !busy) { Text("驳回") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onApprove, enabled = !busy) { Text("通过") }
            }
        }
    }
}

@Composable
private fun PublishedTab(vm: AuditViewModel) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "已上架 ${vm.published.size} 个",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            if (vm.loadingPublished) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            TextButton(onClick = { vm.refreshPublished() }) { Text("刷新") }
        }

        if (vm.published.isEmpty()) {
            EmptyHint(if (vm.loadingPublished) "正在读取索引…" else "索引为空，或尚未拉取")
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
            ) {
                items(vm.published, key = { it.id }) { item ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Spacer(Modifier.width(8.dp))
                                Badge("v${item.version}")
                                Spacer(Modifier.weight(1f))
                                Text(formatSize(item.sizeBytes), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(item.id, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.secondary)
                            if (item.tags.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(item.tags.joinToString(" · "), fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                            item.downloadUrl?.let { url ->
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                ) { Text("打开下载地址", fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(vm: AuditViewModel) {
    var endpoint by remember { mutableStateOf(vm.endpoint) }
    var token by remember { mutableStateOf(vm.token) }
    val scroll = rememberScrollState()

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)
    ) {
        Text("审核服务", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            label = { Text("Worker 端点") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("管理员令牌（X-Admin-Token）") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row {
            Button(onClick = { vm.saveConfig(endpoint, token) }) { Text("保存") }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(onClick = { vm.testConnection() }, enabled = !vm.testing) {
                Text(if (vm.testing) "正在测试…" else "测试连接")
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("说明", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "· 「通过」会让 Worker 重新校验待审 ZIP 的 SHA-256，一致才写入 modules/ 并合并索引。\n" +
                "· 「驳回」只记录原因并关闭 issue，不上架。\n" +
                "· 每次提交都会自动开一个 submission issue，审核结果会评论并关闭它。\n" +
                "· 令牌只保存在本机，不会写入日志或随请求以外的地方发送。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.secondary,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun Badge(text: String) {
    Box(
        Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0)
    bytes >= 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatTime(seconds: Long): String {
    if (seconds <= 0L) return "—"
    val format = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return format.format(Date(seconds * 1000L))
}
