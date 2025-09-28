package huedes.unraid.unraid_cleanarr// oder dein gewählter Package-Name
// Import für die Farbe aus deinem Theme
import huedes.unraid.unraid_cleanarr.ui.theme.SecondaryText

// Import für den Schatten-Effekt
import androidx.compose.ui.draw.shadow
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import huedes.unraid.unraid_cleanarr.ui.theme.UnraidControllerTheme // Wichtig: Den richtigen Theme-Import verwenden
import huedes.unraid.unraid_cleanarr.ui.theme.neonTextStyle // Import für den Neon-Stil


// ============== KONFIGURATION ==============
private const val SERVER_IP = "192.168.2.10" // Trage hier deine Server-IP ein
private const val SERVER_PORT = 5001 // Der Port, den du im Docker Befehl verwendet hast
private const val API_KEY = "XlmMno+82**" // Trage hier deinen API-Schlüssel ein
// ===========================================

// Datenklassen
data class FileItem(val name: String, val type: String)
data class ServerResponse(val path: String, val contents: List<FileItem>)

// Das "Gehirn" der App
class MainViewModel : ViewModel() {
    var fileList by mutableStateOf<List<FileItem>>(emptyList())
    var currentPath by mutableStateOf("")
    var isLoading by mutableStateOf(true)
    var errorMessage by mutableStateOf<String?>(null)
    var scriptOutput by mutableStateOf<List<String>>(emptyList())
    var isScriptRunning by mutableStateOf(false)
    private var webSocket: WebSocket? = null

    init {
        fetchFiles("")
    }

    fun fetchFiles(path: String) {
        isLoading = true
        viewModelScope.launch {
            try {
                val response = ApiClient.fetchFileList(path)
                fileList = response.contents
                currentPath = path
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = "Fehler: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun navigateTo(folder: String) {
        val newPath = if (currentPath.isEmpty()) folder else "$currentPath/$folder"
        fetchFiles(newPath)
    }

    fun navigateBack() {
        val newPath = if (currentPath.contains('/')) currentPath.substringBeforeLast('/') else ""
        fetchFiles(newPath)
    }

    fun runScript() {
        if (isScriptRunning) return
        scriptOutput = emptyList()
        isScriptRunning = true
        webSocket = ApiClient.executeScript(
            onMessage = { message -> scriptOutput = scriptOutput + message },
            onFailure = { error ->
                scriptOutput = scriptOutput + "FEHLER: $error"
                isScriptRunning = false
            },
            onClosing = { isScriptRunning = false }
        )
    }
}

// Haupt-Aktivität der App
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UnraidControllerTheme {
                MainScreen(viewModel = viewModel()) // Korrekte Initialisierung des ViewModels
            }
        }
    }
}

// Hauptbildschirm mit Navigation zwischen den Tabs
// Angepasste MainScreen Funktion
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Datei-Browser", "Skript ausführen")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Unraid Controller",
                        // Wende den Neon-Stil mit der Primärfarbe an
                        style = neonTextStyle(color = MaterialTheme.colorScheme.primary)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        content = { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) },
                            icon = {
                                if (index == 0) Icon(Icons.Outlined.List, contentDescription = null)
                                else Icon(Icons.Outlined.Computer, contentDescription = null)
                            },
                            // Setze die Farben für aktive und inaktive Tabs
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = SecondaryText
                        )
                    }
                }
                when (selectedTab) {
                    0 -> FileBrowserScreen(viewModel)
                    1 -> ScriptRunnerScreen(viewModel)
                }
            }
        }
    )
}


// Bildschirm 1: Der Datei-Browser
@Composable
fun FileBrowserScreen(viewModel: MainViewModel) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (viewModel.currentPath.isNotEmpty()) {
                IconButton(onClick = { viewModel.navigateBack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                }
            }
            Text("/${viewModel.currentPath}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))

        when {
            viewModel.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            viewModel.errorMessage != null -> Text(text = viewModel.errorMessage!!, color = MaterialTheme.colorScheme.error)
            else -> {
                LazyColumn {
                    items(viewModel.fileList) { fileItem ->
                        FileListItem(fileItem = fileItem) {
                            if (fileItem.type == "directory") {
                                viewModel.navigateTo(fileItem.name)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Einzelner, klickbarer Eintrag in der Dateiliste
@Composable
fun FileListItem(fileItem: FileItem, onClick: () -> Unit) {
    // Der Card wurde ein subtiler Schein hinzugefügt
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(
                elevation = 8.dp,
                shape = MaterialTheme.shapes.medium,
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = if (fileItem.type == "directory") Icons.Default.Folder else Icons.Default.Description,
                contentDescription = fileItem.type,
                modifier = Modifier.size(40.dp),
                // Das Icon im aktiven Tab leuchtet auch
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = fileItem.name, fontSize = 16.sp)
        }
    }
}

// Bildschirm 2: Skript starten und Ausgabe anzeigen
@Composable
fun ScriptRunnerScreen(viewModel: MainViewModel) {
    val listState = rememberLazyListState()
    LaunchedEffect(viewModel.scriptOutput.size) {
        if (viewModel.scriptOutput.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.scriptOutput.size - 1)
        }
    }
    Column(modifier = Modifier.padding(16.dp)) {
        Button(
            onClick = { viewModel.runScript() },
            enabled = !viewModel.isScriptRunning,
            modifier = Modifier.fillMaxWidth(),
            // Gib dem Button einen leichten Schein
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 2.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Start")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (viewModel.isScriptRunning) "Skript läuft..." else "Skript starten",
                // Auch der Button-Text bekommt den Neon-Glow
                style = neonTextStyle(color = MaterialTheme.colorScheme.primary)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(state = listState, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                items(viewModel.scriptOutput) { line ->
                    Text(line, fontFamily = FontFamily.Monospace, color = SecondaryText)
                }
            }
        }
    }
}

// Netzwerklogik (HTTP und WebSocket)
object ApiClient {
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val gson = Gson()

    suspend fun fetchFileList(path: String): ServerResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("http://$SERVER_IP:$SERVER_PORT/files/$path")
            .header("X-API-Key", API_KEY)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unerwarteter Code ${response.code}")
            val body = response.body?.string() ?: throw IOException("Leere Antwort")
            gson.fromJson(body, object : TypeToken<ServerResponse>() {}.type)
        }
    }

    fun executeScript(onMessage: (String) -> Unit, onFailure: (String) -> Unit, onClosing: () -> Unit): WebSocket {
        val request = Request.Builder().url("ws://$SERVER_IP:$SERVER_PORT/ws/run-script").build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send(API_KEY) }
            override fun onMessage(webSocket: WebSocket, text: String) { onMessage(text) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                onClosing()
                webSocket.close(1000, null)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onFailure(t.message ?: "Unbekannter Fehler")
                onClosing()
            }
        }
        return client.newWebSocket(request, listener)
    }
}

// Standard Theme
@Composable
fun UnraidControllerTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}