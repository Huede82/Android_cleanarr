package huedes.unraid.unraid_cleanarr // Ihr Package-Name

import android.Manifest
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import huedes.unraid.unraid_cleanarr.ui.theme.SecondaryText
import huedes.unraid.unraid_cleanarr.ui.theme.UnraidControllerTheme
import huedes.unraid.unraid_cleanarr.ui.theme.neonTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

// ============== KONFIGURATION (AUS local.properties) ==============
private val LOCAL_IP = BuildConfig.SERVER_IP
private val REMOTE_URL = BuildConfig.REMOTE_URL
private const val SERVER_PORT = 5001
private val API_KEY = BuildConfig.API_KEY
private val HOME_WIFI_SSIDS = listOf("\"By Lemming\"", "\"By Lemming_5G\"")
// ===============================================================

enum class ConnectionType { HEIMNETZ, REMOTE }
enum class FileStatus { FOUND, MOVING, FINISHED_SUCCESS, FINISHED_ERROR }
data class MonitoredFile(val id: String, val name: String, val status: FileStatus = FileStatus.FOUND, val errorMessage: String? = null)
data class ScriptEvent(@SerializedName("event") val eventType: String, val id: String?, val fileName: String?, val status: String?, val message: String?)
data class FileItem(val name: String, val type: String)
data class ServerResponse(val path: String, val contents: List<FileItem>)


class MainViewModel(application: Application) : AndroidViewModel(application) {
    var fileList by mutableStateOf<List<FileItem>>(emptyList())
    var currentPath by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var monitoredFiles by mutableStateOf<Map<String, MonitoredFile>>(emptyMap())
    var isScriptRunning by mutableStateOf(false)
    var connectionType by mutableStateOf(ConnectionType.REMOTE)
    private var serverAddress by mutableStateOf(REMOTE_URL)

    private var webSocket: WebSocket? = null
    private val gson = Gson()

    fun refreshConnectionAndData() {
        updateNetworkState()
        fetchFiles(currentPath)
    }

    private fun updateNetworkState() {
        val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        var isInHomeWifi = false
        if (networkCapabilities != null && networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
            try {
                val wifiManager = getApplication<Application>().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val ssid = wifiManager.connectionInfo.ssid
                if (HOME_WIFI_SSIDS.contains(ssid)) isInHomeWifi = true
            } catch (e: SecurityException) {
                isInHomeWifi = false
            }
        }
        connectionType = if (isInHomeWifi) ConnectionType.HEIMNETZ else ConnectionType.REMOTE
        serverAddress = if (isInHomeWifi) LOCAL_IP else REMOTE_URL
    }

    fun runScript() {
        if (isScriptRunning) return
        updateNetworkState()
        monitoredFiles = emptyMap()
        isScriptRunning = true
        webSocket = ApiClient.executeScript(serverAddress, connectionType,
            onMessage = { jsonString ->
                try {
                    val event = gson.fromJson(jsonString, ScriptEvent::class.java)
                    val newMap = monitoredFiles.toMutableMap()
                    when (event.eventType) {
                        "found" -> event.id?.let { newMap[it] = MonitoredFile(id = it, name = event.fileName ?: "Unbekannt") }
                        "moving" -> event.id?.let { id ->
                            val existingFile = newMap[id]
                            if (existingFile != null) newMap[id] = existingFile.copy(status = FileStatus.MOVING)
                        }
                        "finished" -> event.id?.let { id ->
                            val existingFile = newMap[id]
                            if (existingFile != null) {
                                val newStatus = if (event.status == "success") FileStatus.FINISHED_SUCCESS else FileStatus.FINISHED_ERROR
                                val newErrorMessage = if (event.status != "success") event.message else null
                                newMap[id] = existingFile.copy(status = newStatus, errorMessage = newErrorMessage)
                            }
                        }
                    }
                    monitoredFiles = newMap
                } catch (e: JsonSyntaxException) { /* Ignoriere ungültiges JSON */ }
            },
            onFailure = { isScriptRunning = false },
            onClosing = {
                val finalMap = monitoredFiles.toMutableMap()
                var changed = false
                finalMap.values.filter { it.status == FileStatus.MOVING }.forEach {
                    val updatedFile = it.copy(status = FileStatus.FINISHED_ERROR, errorMessage = "Status unklar, Verbindung beendet.")
                    finalMap[it.id] = updatedFile
                    changed = true
                }
                if (changed) monitoredFiles = finalMap
                isScriptRunning = false
            }
        )
    }

    fun fetchFiles(path: String) {
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            updateNetworkState()
            try {
                val response = ApiClient.fetchFileList(serverAddress, connectionType, path)
                fileList = response.contents
                currentPath = path
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
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UnraidControllerTheme {
                val viewModel: MainViewModel = viewModel()
                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = {
                        viewModel.refreshConnectionAndData()
                    }
                )
                LaunchedEffect(Unit) {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Datei-Browser", "Skript ausführen")
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Huede's Cleanarr", style = neonTextStyle(color = MaterialTheme.colorScheme.primary)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    val statusText = if (viewModel.connectionType == ConnectionType.HEIMNETZ) "Heimnetz" else "Remote"
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(end = 16.dp),
                        color = if (viewModel.connectionType == ConnectionType.HEIMNETZ) MaterialTheme.colorScheme.primary else SecondaryText,
                        fontWeight = FontWeight.Bold
                    )
                }
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

@Composable
fun FileBrowserScreen(viewModel: MainViewModel) {
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
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
            viewModel.errorMessage != null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = viewModel.errorMessage!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.refreshConnectionAndData() }) { Text("Erneut versuchen") }
                }
            }
            else -> {
                LazyColumn {
                    items(viewModel.fileList) { fileItem ->
                        FileListItem(fileItem = fileItem) {
                            if (fileItem.type == "directory") viewModel.navigateTo(fileItem.name)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScriptRunnerScreen(viewModel: MainViewModel) {
    val listState = rememberLazyListState()
    val filesToDisplay = viewModel.monitoredFiles.values.toList().sortedBy { it.name }
    LaunchedEffect(filesToDisplay.size) {
        if (filesToDisplay.isNotEmpty()) listState.animateScrollToItem(filesToDisplay.size - 1)
    }
    Column(modifier = Modifier.padding(16.dp)) {
        Button(
            onClick = { viewModel.runScript() },
            enabled = !viewModel.isScriptRunning,
            modifier = Modifier.fillMaxWidth(),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 2.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Start")
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (viewModel.isScriptRunning) "Skript läuft..." else "Skript starten", style = neonTextStyle(color = MaterialTheme.colorScheme.primary))
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filesToDisplay, key = { it.id }) { file ->
                FileStatusCard(file = file)
            }
        }
    }
}

@Composable
fun FileStatusCard(file: MonitoredFile) {
    val cardColor by animateColorAsState(
        targetValue = when (file.status) {
            FileStatus.FOUND -> MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            FileStatus.MOVING -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
            FileStatus.FINISHED_SUCCESS -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            FileStatus.FINISHED_ERROR -> MaterialTheme.colorScheme.errorContainer
        }, label = "card color animation"
    )
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardColor)) {
        Row(modifier = Modifier.padding(16.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                when (file.status) {
                    FileStatus.FOUND -> Icon(Icons.Default.Search, contentDescription = "Gefunden", tint = SecondaryText)
                    FileStatus.MOVING -> CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onSurface)
                    FileStatus.FINISHED_SUCCESS -> Icon(Icons.Default.Done, contentDescription = "Fertig", tint = MaterialTheme.colorScheme.primary)
                    FileStatus.FINISHED_ERROR -> Icon(Icons.Default.Close, contentDescription = "Fehler", tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = file.name, fontWeight = FontWeight.Bold, maxLines = 1)
                file.errorMessage?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
fun FileListItem(fileItem: FileItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(elevation = 8.dp, shape = MaterialTheme.shapes.medium, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = if (fileItem.type == "directory") Icons.Default.Folder else Icons.Default.Description,
                contentDescription = fileItem.type,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = fileItem.name, fontSize = 16.sp, maxLines = 1)
        }
    }
}

object ApiClient {
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val gson = Gson()
    suspend fun fetchFileList(serverAddress: String, connectionType: ConnectionType, path: String): ServerResponse = withContext(Dispatchers.IO) {
        val scheme = if (connectionType == ConnectionType.HEIMNETZ) "http" else "https"
        val port = if (connectionType == ConnectionType.HEIMNETZ) ":$SERVER_PORT" else ""
        // URL-Konstruktion, die den Schrägstrich am Ende erzwingt
        val endpoint = if (path.isEmpty()) "files/" else "files/$path"
        val url = "$scheme://$serverAddress$port/$endpoint"
        val request = Request.Builder().url(url).header("X-API-Key", API_KEY).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unerwarteter Code ${response.code} für URL $url")
            val body = response.body?.string() ?: throw IOException("Leere Antwort")
            gson.fromJson(body, object : TypeToken<ServerResponse>() {}.type)
        }
    }
    fun executeScript(serverAddress: String, connectionType: ConnectionType, onMessage: (String) -> Unit, onFailure: (String) -> Unit, onClosing: () -> Unit): WebSocket {
        val scheme = if (connectionType == ConnectionType.HEIMNETZ) "ws" else "wss"
        val port = if (connectionType == ConnectionType.HEIMNETZ) ":$SERVER_PORT" else ""
        val url = "$scheme://$serverAddress$port/ws/run-script"
        val request = Request.Builder().url(url).build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send(API_KEY) }
            override fun onMessage(webSocket: WebSocket, text: String) { onMessage(text) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { onClosing(); webSocket.close(1000, null) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { onFailure(t.message ?: "Unbekannter Fehler"); onClosing() }
        }
        return client.newWebSocket(request, listener)
    }
}