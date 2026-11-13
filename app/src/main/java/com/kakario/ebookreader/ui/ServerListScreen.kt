package com.kakario.ebookreader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kakario.ebookreader.data.FtpServer

@Composable
fun ServerListScreen(
    servers: List<FtpServer>,
    onOpen: (FtpServer) -> Unit,
    onSave: (FtpServer) -> Unit,
    onDelete: (FtpServer) -> Unit,
) {
    // null = 창 닫힘, id 0 = 새 서버, 그 외 = 수정 중인 서버
    var editing by remember { mutableStateOf<FtpServer?>(null) }
    var deleting by remember { mutableStateOf<FtpServer?>(null) }

    Column(Modifier.fillMaxSize()) {
        BrowserHeader(
            title = "FTP 서버",
            subtitle = if (servers.isEmpty()) "서버를 추가해 주세요" else "${servers.count { com.kakario.ebookreader.data.Privacy.showServer(it.id) }}개",
            onBack = null,
            actions = { TextAction("+ 추가") { editing = NEW_SERVER } },
        )
        Line()
        if (servers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "등록된 FTP 서버가 없어요\n오른쪽 위 '+ 추가'로 넣어 주세요",
                    color = Ink,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(servers.filter { com.kakario.ebookreader.data.Privacy.showServer(it.id) }, key = { it.id }) { server ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(server) }
                            .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (com.kakario.ebookreader.data.Privacy.unlocked && com.kakario.ebookreader.data.Privacy.isServerHidden(server.id)) "(숨김) " else "") + server.name,
                                color = Ink,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "${server.host}:${server.port}  ·  ${server.user.ifBlank { "익명" }}",
                                color = Ink,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (com.kakario.ebookreader.data.Privacy.unlocked) {
                            TextAction(if (com.kakario.ebookreader.data.Privacy.isServerHidden(server.id)) "숨김 풀기" else "숨기기") {
                                com.kakario.ebookreader.data.Privacy.toggle(com.kakario.ebookreader.data.Privacy.serverKey(server.id))
                            }
                        }
                        TextAction("수정") { editing = server }
                        TextAction("삭제") { deleting = server }
                    }
                    Line()
                }
            }
        }
    }

    editing?.let { target ->
        ServerDialog(
            initial = target,
            onDismiss = { editing = null },
            onSave = {
                onSave(it)
                editing = null
            },
        )
    }

    deleting?.let { target ->
        LineDialog(
            title = "서버 삭제",
            onDismiss = { deleting = null },
            confirm = {
                DialogAction("삭제", bold = true) {
                    onDelete(target)
                    deleting = null
                }
            },
            dismiss = { DialogAction("취소") { deleting = null } },
        ) {
            Text("'${target.name}' 서버를 목록에서 지울까요?", color = Ink)
        }
    }
}

private val NEW_SERVER = FtpServer(0, "", "", 21, "", "", "UTF-8")

@Composable
private fun ServerDialog(
    initial: FtpServer,
    onDismiss: () -> Unit,
    onSave: (FtpServer) -> Unit,
) {
    val isNew = initial.id == 0L
    var name by remember { mutableStateOf(initial.name) }
    var host by remember { mutableStateOf(initial.host) }
    var port by remember { mutableStateOf(initial.port.toString()) }
    var user by remember { mutableStateOf(initial.user) }
    var password by remember { mutableStateOf(initial.password) }
    var encoding by remember { mutableStateOf(initial.encoding) }

    val canSave = host.isNotBlank() && (port.toIntOrNull() ?: 0) in 1..65535
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Ink,
        unfocusedBorderColor = Ink,
        focusedLabelColor = Ink,
        unfocusedLabelColor = Ink,
        cursorColor = Ink,
        focusedTextColor = Ink,
        unfocusedTextColor = Ink,
        focusedContainerColor = Paper,
        unfocusedContainerColor = Paper,
    )

    LineDialog(
        title = if (isNew) "FTP 서버 추가" else "서버 정보 수정",
        onDismiss = onDismiss,
        confirm = {
            DialogAction("저장", bold = true) {
                if (!canSave) return@DialogAction
                onSave(
                    FtpServer(
                        id = if (isNew) System.currentTimeMillis() else initial.id,
                        name = name.ifBlank { host },
                        host = host,
                        port = port.toInt(),
                        user = user.trim(),
                        password = password,
                        encoding = encoding,
                    )
                )
            }
        },
        dismiss = { DialogAction("취소", onClick = onDismiss) },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("이름 (예: 우리집 NAS)") }, singleLine = true,
                shape = RectangleShape, colors = fieldColors,
            )
            OutlinedTextField(
                value = host, onValueChange = { host = it.trim() },
                label = { Text("주소 (외부 IP 또는 DDNS)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RectangleShape, colors = fieldColors,
            )
            OutlinedTextField(
                value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) },
                label = { Text("포트") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RectangleShape, colors = fieldColors,
            )
            OutlinedTextField(
                value = user, onValueChange = { user = it },
                label = { Text("아이디 (비우면 익명)") }, singleLine = true,
                shape = RectangleShape, colors = fieldColors,
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("비밀번호") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RectangleShape, colors = fieldColors,
            )
            Text(
                "한글 파일명 인코딩",
                color = Ink,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            Column(Modifier.padding(start = 0.dp)) {
                CompactRadio("UTF-8 (기본)", encoding == "UTF-8") { encoding = "UTF-8" }
                CompactRadio("EUC-KR (한글이 깨질 때)", encoding == "EUC-KR") { encoding = "EUC-KR" }
            }
        }
    }
}

@Composable
private fun CompactRadio(label: String, selected: Boolean, onSelect: () -> Unit) {
    Text(
        (if (selected) "●  " else "○  ") + label,
        color = Ink,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
    )
}
