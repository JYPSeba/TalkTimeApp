package com.example.sayonara

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sayonara.ui.theme.SayonaraTheme
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Locale

// --- CONFIGURACIÓN DE SUPABASE ---
const val SUPABASE_URL = "TU_SUPABASE_URL"
const val SUPABASE_KEY = "TU_SUPABASE_KEY"

class MainActivity : ComponentActivity() {

    // Inicialización del cliente de Supabase con el módulo Realtime
    private val supabase = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Realtime)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SayonaraTheme {
                TrafficLightApp(supabase)
            }
        }
    }
}

// Estados de navegación simple
enum class AppState {
    ROLE_SELECTION, ORGANIZER, STAGE
}

@Composable
fun TrafficLightApp(supabase: SupabaseClient) {
    var currentState by remember { mutableStateOf(AppState.ROLE_SELECTION) }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (currentState) {
            AppState.ROLE_SELECTION -> RoleSelectionScreen { currentState = it }
            AppState.ORGANIZER -> OrganizerScreen(supabase) { currentState = AppState.ROLE_SELECTION }
            AppState.STAGE -> StageScreen(supabase) { currentState = AppState.ROLE_SELECTION }
        }
    }
}

@Composable
fun RoleSelectionScreen(onRoleSelected: (AppState) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Semáforo de Escenario", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { onRoleSelected(AppState.ORGANIZER) },
            modifier = Modifier.fillMaxWidth(0.8f).height(64.dp)
        ) {
            Text("SOY ORGANIZADOR", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { onRoleSelected(AppState.STAGE) },
            modifier = Modifier.fillMaxWidth(0.8f).height(64.dp)
        ) {
            Text("PANTALLA DE ESCENARIO", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OrganizerScreen(supabase: SupabaseClient, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val channel = remember { supabase.realtime.channel("canal-escenario") {} }

    var selectedMinutes by remember { mutableIntStateOf(0) }
    var selectedSeconds by remember { mutableIntStateOf(0) }
    var remainingTime by remember { mutableLongStateOf(0L) }
    var isTimerRunning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        channel.subscribe()
    }

    // Lógica del Temporizador optimizada
    LaunchedEffect(isTimerRunning) {
        if (isTimerRunning) {
            while (remainingTime > 0) {
                delay(1000L)
                remainingTime -= 1
            }
            if (remainingTime == 0L) {
                isTimerRunning = false
                channel.broadcast(
                    event = "status",
                    message = buildJsonObject { put("status", "tiempo_agotado") }
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CONTROL DE ESCENARIO",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        // Card del Temporizador Profesional
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Configurar Tiempo",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                if (!isTimerRunning) {
                    Row(
                        modifier = Modifier.height(150.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimeWheelPicker(
                            range = 0..60,
                            label = "MIN",
                            onValueChange = { selectedMinutes = it }
                        )
                        Text(
                            ":",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Light,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        TimeWheelPicker(
                            range = 0..59,
                            label = "SEG",
                            onValueChange = { selectedSeconds = it }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            val totalSeconds = (selectedMinutes * 60 + selectedSeconds).toLong()
                            if (totalSeconds > 0) {
                                remainingTime = totalSeconds
                                isTimerRunning = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("INICIAR CUENTA ATRÁS")
                    }
                } else {
                    val displayMins = remainingTime / 60
                    val displaySecs = remainingTime % 60
                    
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", displayMins, displaySecs),
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Thin,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = { isTimerRunning = false; remainingTime = 0 },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("CANCELAR", color = Color.Red)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider(modifier = Modifier.alpha(0.2f))
        Spacer(modifier = Modifier.height(32.dp))

        // Botones de Acción Directa
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        channel.broadcast(
                            event = "status",
                            message = buildJsonObject { put("status", "tiempo_agotado") }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(80.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                shape = MaterialTheme.shapes.large
            ) {
                Text("AVISAR: TIEMPO AGOTADO", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    scope.launch {
                        channel.broadcast(
                            event = "status",
                            message = buildJsonObject { put("status", "tiempo_urgente") }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(80.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                shape = MaterialTheme.shapes.large
            ) {
                Text("⚠️ ¡URGENCIA MÁXIMA!", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }

            Button(
                onClick = {
                    isTimerRunning = false
                    remainingTime = 0
                    scope.launch {
                        channel.broadcast(
                            event = "status",
                            message = buildJsonObject { put("status", "reiniciar") }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Text("REINICIAR ESCENARIO", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text("← Salir del Panel", color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun TimeWheelPicker(
    range: IntRange,
    label: String,
    onValueChange: (Int) -> Unit
) {
    val items = range.toList()
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    
    val selectedIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex < items.size) {
            onValueChange(items[selectedIndex])
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier.height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            // Highlighting middle area
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.small
            ) {}
            
            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top padding to allow centering first item
                item { Spacer(modifier = Modifier.height(40.dp)) }
                
                items(items.size) { index ->
                    val isSelected = selectedIndex == index
                    Text(
                        text = String.format(Locale.getDefault(), "%02d", items[index]),
                        fontSize = if (isSelected) 32.sp else 24.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                
                // Bottom padding to allow centering last item
                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
fun StageScreen(supabase: SupabaseClient, onBack: () -> Unit) {
    val context = LocalContext.current
    var statusState by remember { mutableStateOf("espera") } // "espera", "tiempo_agotado", "tiempo_urgente"

    // Animación para el estado urgente
    var flashToggle by remember { mutableStateOf(false) }
    LaunchedEffect(statusState) {
        if (statusState == "tiempo_urgente") {
            while (true) {
                flashToggle = !flashToggle
                delay(500)
            }
        }
    }

    // Lógica para mantener la pantalla encendida
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Suscripción al canal de Supabase Realtime
    LaunchedEffect(Unit) {
        val channel: RealtimeChannel = supabase.realtime.channel("canal-escenario") {}

        channel.broadcastFlow<JsonObject>(event = "status")
            .onEach { payload: JsonObject ->
                val status = payload["status"]?.jsonPrimitive?.contentOrNull
                if (status != null) {
                    statusState = status
                }
            }
            .launchIn(this)

        channel.subscribe()
    }

    val backgroundColor = when (statusState) {
        "tiempo_agotado" -> Color.Red
        "tiempo_urgente" -> if (flashToggle) Color.Yellow else Color.Red
        else -> Color.Black
    }

    val textColor = when (statusState) {
        "tiempo_urgente" -> Color.Black
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        when (statusState) {
            "tiempo_agotado", "tiempo_urgente" -> {
                Text(
                    text = if (statusState == "tiempo_urgente") "¡¡URGENTE!!" else "TIEMPO",
                    color = textColor,
                    fontSize = if (statusState == "tiempo_urgente") 80.sp else 100.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
            else -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Esperando señal...",
                        color = Color.DarkGray,
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.height(40.dp))
                    TextButton(onClick = onBack) {
                        Text("Salir del modo escenario", color = Color.Gray)
                    }
                }
            }
        }
    }
}
