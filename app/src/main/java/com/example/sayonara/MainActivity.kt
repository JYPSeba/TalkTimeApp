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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// --- CONFIGURACIÓN DE SUPABASE ---
// TODO: Reemplaza con tus propias credenciales de Supabase
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
    // En v3.5.0 se usa .realtime.channel("nombre") { }
    val channel = remember { supabase.realtime.channel("canal-escenario") {} }

    LaunchedEffect(Unit) {
        channel.subscribe()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Botón para Activar Tiempo Agotado
        Button(
            onClick = {
                scope.launch {
                    channel.broadcast(
                        event = "status",
                        message = buildJsonObject { put("status", "tiempo_agotado") }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().height(200.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            shape = MaterialTheme.shapes.large
        ) {
            Text(
                "ENVIAR\nTIEMPO",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                lineHeight = 36.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Botón para Reiniciar a estado de espera
        Button(
            onClick = {
                scope.launch {
                    channel.broadcast(
                        event = "status",
                        message = buildJsonObject { put("status", "reiniciar") }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().height(80.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), // Verde
            shape = MaterialTheme.shapes.large
        ) {
            Text("REINICIAR / LIMPIAR", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(48.dp))

        TextButton(onClick = onBack) {
            Text("Cerrar Sesión")
        }
    }
}

@Composable
fun StageScreen(supabase: SupabaseClient, onBack: () -> Unit) {
    val context = LocalContext.current
    var isTimeUp by remember { mutableStateOf(false) }

    // Lógica para mantener la pantalla encendida (Prevención de suspensión)
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

        // Escuchamos los eventos de broadcast
        channel.broadcastFlow<JsonObject>(event = "status")
            .onEach { payload: JsonObject ->
                val status = payload["status"]?.jsonPrimitive?.contentOrNull
                when (status) {
                    "tiempo_agotado" -> isTimeUp = true
                    "reiniciar" -> isTimeUp = false
                }
            }
            .launchIn(this)

        channel.subscribe()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isTimeUp) Color.Red else Color.Black)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isTimeUp) {
            Text(
                text = "TIEMPO",
                color = Color.White,
                fontSize = 100.sp,
                fontWeight = FontWeight.Black
            )
        } else {
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
