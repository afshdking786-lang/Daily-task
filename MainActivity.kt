package com.example.dailytaskmanager

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Task(val id: Long, val title: String, val category: String, val done: Boolean, val date: String)

private val Mint = Color(0xFF39D9A5)
private val Navy = Color(0xFF07111F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DailyTaskApp() }
    }
}

@Composable
fun DailyTaskApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("daily_task_manager", Context.MODE_PRIVATE) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
    var taskText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Personal") }
    var goal by remember { mutableIntStateOf(prefs.getInt("goal", 5)) }
    var goalText by remember { mutableStateOf(goal.toString()) }
    var reminderHour by remember { mutableIntStateOf(prefs.getInt("hour", 20)) }
    var reminderMinute by remember { mutableIntStateOf(prefs.getInt("minute", 0)) }
    var tab by remember { mutableStateOf("Today") }
    var tasks by remember { mutableStateOf(loadTasks(prefs)) }
    var showSettings by remember { mutableStateOf(false) }

    val today = dateKey(System.currentTimeMillis())
    val todayTasks = tasks.filter { it.date == today }
    val completedToday = todayTasks.count { it.done }
    val streak = calculateStreak(tasks)
    val completedAll = tasks.count { it.done }
    val bg = if (dark) Navy else Color(0xFFF3F7FA)
    val panel = if (dark) Color(0xFF102238) else Color.White
    val fg = if (dark) Color.White else Color(0xFF152337)
    val muted = if (dark) Color(0xFF9FB2C8) else Color(0xFF61738A)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Mint, background = bg, surface = panel) else lightColorScheme(primary = Color(0xFF008C69), background = bg, surface = panel)) {
        Surface(Modifier.fillMaxSize(), color = bg) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("My Daily Tasks ✨", style = MaterialTheme.typography.headlineSmall, color = fg, fontWeight = FontWeight.Bold)
                        Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()), color = muted)
                    }
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Default.DarkMode, contentDescription = "Settings and dark mode", tint = Mint)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Today's progress", color = muted)
                        Spacer(Modifier.height(6.dp))
                        Text("$completedToday / $goal tasks complete", color = fg, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(progress = { (completedToday.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), color = Mint)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🔥 $streak day streak", color = Mint, fontWeight = FontWeight.SemiBold)
                            Text("$completedAll completed total", color = muted)
                        }
                    }
                }
                if (showSettings) {
                    Spacer(Modifier.height(10.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = panel)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Dark mode", color = fg)
                                Switch(checked = dark, onCheckedChange = { dark = it; prefs.edit().putBoolean("dark", it).apply() })
                            }
                            OutlinedTextField(value = goalText, onValueChange = { goalText = it.filter(Char::isDigit) }, label = { Text("Daily goal (tasks)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                            Button(onClick = { goal = (goalText.toIntOrNull() ?: 5).coerceIn(1, 100); prefs.edit().putInt("goal", goal).apply(); goalText = goal.toString() }) { Text("Save goal") }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Mint)
                                Spacer(Modifier.width(8.dp))
                                Text("Daily reminder: ${"%02d".format(reminderHour)}:${"%02d".format(reminderMinute)}", color = fg)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { reminderHour = (reminderHour + 1) % 24; prefs.edit().putInt("hour", reminderHour).apply() }) { Text("Hour +1") }
                                OutlinedButton(onClick = { reminderMinute = if (reminderMinute == 0) 30 else 0; prefs.edit().putInt("minute", reminderMinute).apply() }) { Text("Minute") }
                                Button(onClick = {
                                    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    scheduleReminder(context, reminderHour, reminderMinute)
                                }) { Text("Set reminder") }
                            }
                            Text("Reminder repeats daily. Allow notifications when Android asks.", color = muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Today", "History").forEach { name ->
                        FilterChip(selected = tab == name, onClick = { tab = name }, label = { Text(name) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (tab == "Today") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = taskText, onValueChange = { taskText = it }, modifier = Modifier.weight(1f), placeholder = { Text("Add a task…") }, singleLine = true)
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(onClick = {
                            if (taskText.isNotBlank()) {
                                val t = Task(System.currentTimeMillis(), taskText.trim(), category, false, today)
                                tasks = tasks + t
                                saveTasks(prefs, tasks)
                                taskText = ""
                            }
                        }) { Icon(Icons.Default.Add, contentDescription = "Add task") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Personal", "Work", "Health", "Study", "Other").forEach { c ->
                            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (todayTasks.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("No tasks yet. Add one small win for today 💪", color = muted)
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(todayTasks, key = { it.id }) { task ->
                                Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(14.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = task.done, onCheckedChange = { checked ->
                                            tasks = tasks.map { if (it.id == task.id) it.copy(done = checked) else it }
                                            saveTasks(prefs, tasks)
                                        })
                                        Column(Modifier.weight(1f)) {
                                            Text(task.title, color = if (task.done) muted else fg, fontWeight = FontWeight.Medium)
                                            Text(task.category, color = muted, style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(onClick = { tasks = tasks.filterNot { it.id == task.id }; saveTasks(prefs, tasks) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete task", tint = muted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val history = tasks.filter { it.done }.sortedByDescending { it.date }
                    if (history.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("Completed tasks will appear here.", color = muted) }
                    } else {
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(history, key = { it.id }) { task ->
                                Card(colors = CardDefaults.cardColors(containerColor = panel)) {
                                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Mint)
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(task.title, color = fg)
                                            Text("${task.category} • ${task.date}", color = muted, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Your tasks are saved on this device.", color = muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

fun dateKey(time: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(time))

fun loadTasks(prefs: android.content.SharedPreferences): List<Task> {
    return try {
        val array = JSONArray(prefs.getString("tasks", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Task(o.getLong("id"), o.getString("title"), o.optString("category", "Personal"), o.optBoolean("done"), o.getString("date"))
        }
    } catch (_: Exception) { emptyList() }
}

fun saveTasks(prefs: android.content.SharedPreferences, tasks: List<Task>) {
    val array = JSONArray()
    tasks.forEach { t ->
        array.put(JSONObject().put("id", t.id).put("title", t.title).put("category", t.category).put("done", t.done).put("date", t.date))
    }
    prefs.edit().putString("tasks", array.toString()).apply()
}

fun calculateStreak(tasks: List<Task>): Int {
    val completedDates = tasks.filter { it.done }.map { it.date }.toSet()
    if (completedDates.isEmpty()) return 0
    val cal = Calendar.getInstance()
    val today = dateKey(cal.timeInMillis)
    if (today !in completedDates) cal.add(Calendar.DAY_OF_YEAR, -1)
    var streak = 0
    while (dateKey(cal.timeInMillis) in completedDates) {
        streak++
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }
    return streak
}

fun scheduleReminder(context: Context, hour: Int, minute: Int) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, ReminderReceiver::class.java)
    val pending = PendingIntent.getBroadcast(context, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }
    alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pending)
}
