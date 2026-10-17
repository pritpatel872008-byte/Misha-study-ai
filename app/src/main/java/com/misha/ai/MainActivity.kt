package com.misha.ai

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private val Night = Color(0xFF080A12)
private val Panel = Color(0xFF141827)
private val Violet = Color(0xFF9A6BFF)
private val Cyan = Color(0xFF58D8F5)
private val MainText = Color(0xFFF4F3FA)
private val Muted = Color(0xFF9BA3B8)

private data class ChatMessage(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { MishaApp(onSpeak = { text -> tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "misha-response") }) }
    }
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
    }
    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MishaApp(onSpeak: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var backendUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready when you are") }
    val messages = remember {
        mutableStateListOf(
            ChatMessage("Hey! I'm Misha, your personal AI assistant. 💜", false),
            ChatMessage("Type a message, use speech input, or ask me to open an app.", false)
        )
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) input = spoken
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchSpeech(context) { speechLauncher.launch(it) }
        else status = "Microphone permission denied"
    }

    fun sendMessage() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        input = ""
        messages.add(ChatMessage(text, true))
        val lower = text.lowercase(Locale.getDefault())
        val appMatch = Regex("""(?:open|launch|start)\\s+(.+)""").find(lower)
        if (appMatch != null) {
            val appName = appMatch.groupValues[1].trim()
            val packageName = when {
                "whatsapp" in appName -> "com.whatsapp"
                "youtube" in appName -> "com.google.android.youtube"
                "chrome" in appName -> "com.android.chrome"
                "settings" in appName -> "com.android.settings"
                "camera" in appName -> "com.sec.android.app.camera"
                "maps" in appName -> "com.google.android.apps.maps"
                else -> appName
            }
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                messages.add(ChatMessage("Opening $appName. 💜", false))
                status = "Action completed"
            } else {
                messages.add(ChatMessage("I couldn't find the app '$appName'. Check its installed name and try again.", false))
                status = "App not found"
            }
            return
        }
        if (backendUrl.isBlank()) {
            messages.add(ChatMessage("My AI backend isn't connected yet. Open Settings and add your HTTPS backend URL to enable Gemini replies. I won't pretend a placeholder is a real AI answer.", false))
            status = "Backend not configured"
            return
        }
        busy = true
        status = "Thinking…"
        scope.launch {
            val reply = withContext(Dispatchers.IO) { requestReply(backendUrl, text) }
            messages.add(ChatMessage(reply, false))
            busy = false
            status = if (reply.startsWith("Connection error:")) "Connection issue" else "Reply received"
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = Panel,
            title = { Text("Misha settings", color = MainText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Secure AI backend URL", color = Muted)
                    OutlinedTextField(
                        value = backendUrl,
                        onValueChange = { backendUrl = it.trim() },
                        placeholder = { Text("https://your-host/api/chat") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MainText,
                            unfocusedTextColor = MainText,
                            focusedBorderColor = Violet,
                            unfocusedBorderColor = Muted
                        )
                    )
                    Text("Keep your Gemini API key on the server, never in the APK.", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Save", color = Cyan) } },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Cancel", color = Muted) } }
        )
    }

    Scaffold(containerColor = Night) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .background(Brush.verticalGradient(listOf(Color(0xFF11172A), Night, Color(0xFF100A1D))))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(48.dp).background(Brush.linearGradient(listOf(Violet, Cyan)), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SmartToy, contentDescription = "Misha AI", tint = Night, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("MISHA AI", color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(status, color = Muted, fontSize = 12.sp)
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Cyan)
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().height(120.dp).background(
                    Brush.verticalGradient(listOf(Color(0xFF28225A), Color(0xFF101322))),
                    RoundedCornerShape(24.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(76.dp).background(Brush.linearGradient(listOf(Violet, Cyan)), CircleShape).padding(3.dp)) {
                    Box(Modifier.fillMaxSize().background(Night, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = Violet, modifier = Modifier.size(38.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("CONVERSATION", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(messages) { message ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
                        Column(
                            Modifier.widthIn(max = 310.dp).background(
                                if (message.fromUser) Color(0xFF312456) else Panel,
                                RoundedCornerShape(18.dp)
                            ).padding(13.dp)
                        ) {
                            Text(if (message.fromUser) "YOU" else "MISHA", color = if (message.fromUser) Cyan else Violet, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(message.text, color = MainText, fontSize = 14.sp)
                            if (!message.fromUser) {
                                TextButton(onClick = { onSpeak(message.text) }, contentPadding = PaddingValues(0.dp)) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = "Read aloud", tint = Cyan, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Read aloud", color = Cyan, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                if (busy) item { Text("Misha is thinking…", color = Cyan, fontSize = 12.sp) }
            }
            Row(
                Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(22.dp)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        launchSpeech(context) { speechLauncher.launch(it) }
                    } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Icon(Icons.Default.Mic, contentDescription = "Speak", tint = Cyan) }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = MainText, fontSize = 14.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Cyan),
                    decorationBox = { inner ->
                        Box {
                            if (input.isEmpty()) Text("Talk to Misha…", color = Muted, fontSize = 14.sp)
                            inner()
                        }
                    }
                )
                IconButton(onClick = { sendMessage() }, enabled = !busy) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = if (busy) Muted else Violet)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Speech input uses Android speech services. Always-on wake word and call controls are not enabled yet.", color = Muted, fontSize = 10.sp)
        }
    }
}

private fun launchSpeech(context: android.content.Context, launch: (Intent) -> Unit) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Misha")
    }
    try { launch(intent) } catch (_: ActivityNotFoundException) { }
}

private fun requestReply(endpoint: String, message: String): String {
    return try {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        val payload = JSONObject().put("message", message).toString()
        connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader().use { it.readText() }
        connection.disconnect()
        if (code !in 200..299) "Connection error: server returned HTTP $code"
        else JSONObject(response).optString("reply", "Server response did not include a reply.")
    } catch (e: Exception) {
        "Connection error: ${e.message ?: "Unable to reach the backend"}"
    }
}
