package com.storepos.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.storepos.app.data.StoreRepository
import com.storepos.app.data.model.ShopContext
import com.storepos.app.data.model.SupportMessage
import com.storepos.app.data.model.SupportThread
import com.storepos.app.ui.components.EmptyView
import com.storepos.app.ui.components.LoadingView
import com.storepos.app.ui.components.PageHeader
import kotlinx.coroutines.launch

@Composable
fun SupportPage(context: ShopContext) {
    var threads by remember { mutableStateOf<List<SupportThread>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<List<SupportMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var newOpen by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refreshThreads() {
        threads = StoreRepository.supportThreads(context.shop.id)
        if (selectedId == null || threads.none { it.id == selectedId }) {
            selectedId = threads.firstOrNull()?.id
        }
    }

    suspend fun refreshMessages() {
        messages = selectedId?.let { StoreRepository.supportMessages(it) }.orEmpty()
    }

    suspend fun refreshAll() {
        refreshThreads()
        refreshMessages()
    }

    LaunchedEffect(context.shop.id) {
        runCatching { refreshAll() }.onFailure { error = StoreRepository.userMessage(it) }
        loading = false
    }

    LaunchedEffect(selectedId) {
        if (!loading) {
            runCatching { refreshMessages() }.onFailure { error = StoreRepository.userMessage(it) }
        }
    }

    if (loading) {
        LoadingView("Loading support…")
        return
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader(
            "Support",
            "StorePOS Auto Support with human support handoff",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = {
                        scope.launch {
                            error = null
                            runCatching { refreshAll() }.onFailure { error = StoreRepository.userMessage(it) }
                        }
                    }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                    Button(onClick = { newOpen = true }) {
                        Icon(Icons.Rounded.AddComment, null)
                        Spacer(Modifier.width(6.dp))
                        Text("New chat")
                    }
                }
            }
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (threads.isEmpty()) {
            EmptyView(
                "No support conversations",
                "Start a chat whenever you need help with StorePOS.",
                Modifier.weight(1f)
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(threads, key = { it.id }) { thread ->
                    FilterChip(
                        selected = selectedId == thread.id,
                        onClick = { selectedId = thread.id },
                        label = { Text(thread.subject, maxLines = 1) },
                        leadingIcon = {
                            Icon(Icons.Rounded.SupportAgent, null, Modifier.size(18.dp))
                        }
                    )
                }
            }

            val selected = threads.firstOrNull { it.id == selectedId }
            Card(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(selected?.subject ?: "Support", fontWeight = FontWeight.Bold)
                            val aiState = when {
                                selected?.aiHandoff == true -> "human handoff"
                                selected?.aiEnabled == true -> "Auto Support active"
                                else -> "Auto Support paused"
                            }
                            Text(
                                (selected?.priority ?: "normal") + " priority • " +
                                    (selected?.status ?: "open") + " • " + aiState,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    when {
                                        selected?.aiHandoff == true -> "Human support"
                                        selected?.aiEnabled == true -> "StorePOS Auto Support"
                                        else -> selected?.status ?: "open"
                                    }
                                )
                            }
                        )
                    }

                    HorizontalDivider()

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (messages.isEmpty()) {
                            item {
                                Text(
                                    "No messages yet.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(messages, key = { it.id }) { msg ->
                            val mine = msg.senderType == "customer"
                            val isAi = msg.senderType == "ai"
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = when {
                                        mine -> MaterialTheme.colorScheme.primaryContainer
                                        isAi -> MaterialTheme.colorScheme.tertiaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    modifier = Modifier.widthIn(max = 520.dp)
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(msg.body)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            when {
                                                mine -> "You"
                                                isAi -> "StorePOS Auto Support"
                                                else -> "StorePOS Support"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it.take(4000) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Type a message…") },
                            enabled = selected?.status != "closed",
                            maxLines = 3
                        )
                        FilledIconButton(
                            onClick = {
                                val body = messageText.trim()
                                val thread = selected ?: return@FilledIconButton
                                if (body.isBlank()) return@FilledIconButton
                                scope.launch {
                                    error = null
                                    runCatching {
                                        StoreRepository.sendSupportMessage(
                                            thread.id,
                                            thread.shopId,
                                            body
                                        )
                                        messageText = ""
                                        refreshAll()
                                    }.onFailure { error = StoreRepository.userMessage(it) }
                                }
                            },
                            enabled = messageText.isNotBlank() && selected?.status != "closed"
                        ) {
                            Icon(Icons.Rounded.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }

    if (newOpen) {
        NewSupportDialog(
            onDismiss = { newOpen = false },
            onCreate = { subject, priority, firstMessage ->
                scope.launch {
                    error = null
                    runCatching {
                        val created = StoreRepository.createSupportThread(
                            context.shop.id,
                            subject,
                            priority,
                            firstMessage
                        )
                        selectedId = created.id
                        newOpen = false
                        refreshAll()
                    }.onFailure { error = StoreRepository.userMessage(it) }
                }
            }
        )
    }
}

@Composable
private fun NewSupportDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("normal") }
    var message by remember { mutableStateOf("") }
    var priorityMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New support chat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "StorePOS Auto Support may answer common questions first. Billing, licensing, account and security concerns are handed to human support.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    subject,
                    { subject = it.take(160) },
                    label = { Text("Subject") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Box {
                    OutlinedButton(
                        onClick = { priorityMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Priority: " + priority.replaceFirstChar { it.uppercase() })
                    }
                    DropdownMenu(
                        expanded = priorityMenu,
                        onDismissRequest = { priorityMenu = false }
                    ) {
                        listOf("low", "normal", "high", "urgent").forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    priority = item
                                    priorityMenu = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    message,
                    { message = it.take(4000) },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(subject.trim(), priority, message.trim()) },
                enabled = subject.trim().length >= 3 && message.isNotBlank()
            ) {
                Text("Start chat")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
