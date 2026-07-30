package com.gios.lightglance

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gios.lightglance.hw.LightKey
import com.gios.lightglance.hw.LightKeys
import com.gios.lightglance.hw.LocalWheelBus
import com.gios.lightglance.hw.WheelBus
import com.gios.lightglance.hw.WheelScroll
import com.gios.lightglance.notif.NotifListener
import com.gios.lightglance.notif.NotifStore
import com.gios.lightglance.notif.SlotMap
import com.gios.lightglance.ui.DotField
import com.gios.lightglance.ui.drawGlyph
import com.gios.lightglance.ui.theme.Dim
import com.gios.lightglance.ui.theme.Faint
import com.gios.lightglance.ui.theme.LightGlanceTheme
import com.gios.lightglance.ui.theme.RuleGrey
import kotlinx.coroutines.delay

/**
 * The setup screen, and the only activity in the app that takes the wheel.
 *
 * [GlanceActivity] deliberately does not: it is an ambient surface that is supposed to
 * light up, be read at a glance and go away, so scrolling it would be meaningless, and
 * consuming a key there would be worse than meaningless — every guard in
 * `GlanceController` exists to stop the app trapping the user, and swallowing a hardware
 * key on a screen shown over the keyguard is exactly that shape of bug.
 */
class MainActivity : ComponentActivity() {

    /** Wheel notches on their way to the setup screen. */
    private val wheel = WheelBus()

    /**
     * Every hardware key arrives here first — `DecorView` hands the event to the window
     * callback before it walks the view hierarchy — so a notch reaches the screen whatever
     * happens to hold focus.
     *
     * Only the turns. The wheel click and the camera button belong to LightControl, which
     * owns them phone-wide and passes bare turns through on purpose.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (LightKeys.of(event)) {
            LightKey.WheelUp -> {
                if (event.action == KeyEvent.ACTION_DOWN) wheel.send(1)
                return true
            }
            LightKey.WheelDown -> {
                if (event.action == KeyEvent.ACTION_DOWN) wheel.send(-1)
                return true
            }
            else -> Unit
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LightGlanceTheme {
                CompositionLocalProvider(LocalWheelBus provides wheel) {
                    SetupScreen()
                }
            }
        }
    }
}

@Composable
private fun SetupScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val dots by NotifStore.dots.collectAsStateWithLifecycle()

    // Grants are changed over adb while this screen is open, so poll rather than
    // waiting for a resume that will never come. Each tick costs three binder calls,
    // so this is slow on purpose.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(3_000); tick++ } }

    var mode by remember { mutableStateOf(prefs.mode) }
    var lift by remember { mutableStateOf(prefs.liftToShow) }
    var clock by remember { mutableStateOf(prefs.showClock) }
    var dwell by remember { mutableIntStateOf(prefs.dwellSeconds) }
    var bright by remember { mutableStateOf(prefs.brightness) }

    val listenerOk = remember(tick) { Grants.listenerEnabled(ctx) }
    val overlayOk = remember(tick) { Grants.canStartFromBackground(ctx) }
    val bound = remember(tick) { NotifListener.connected }
    val sleepOk = remember(tick) { Screen.canSleep(ctx) }
    val lastDecision = remember(tick) { GlanceController.lastSkipReason }

    // A long screen: three adb commands, four status rows, a preview, and one row per
    // source the phone has ever notified about.
    val scroll = rememberScrollState()
    WheelScroll(scroll)

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(scroll)
            .padding(horizontal = 22.dp, vertical = 26.dp),
    ) {
        Text("GLANCE", style = MaterialTheme.typography.labelLarge, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(
            "Experimental. Ambient notification dots, not a hardware always-on display.",
            style = MaterialTheme.typography.bodySmall,
            color = Faint,
        )

        SectionHeader("SETUP")
        StatusRow("Notification access", listenerOk)
        StatusRow("Background windows", overlayOk)
        StatusRow("Screen sleep", sleepOk)
        StatusRow("Listener bound", bound)

        if (!listenerOk) CommandBox(Grants.listenerCommand(ctx))
        if (!overlayOk) CommandBox(Grants.overlayCommand(ctx))
        if (!sleepOk) {
            CommandBox(Screen.adminCommand(ctx))
            Text(
                "Optional. Without it the panel stays lit until LightOS's own sleep timer.",
                fontSize = 11.sp,
                color = Faint,
            )
        }

        if (listenerOk && !bound) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Access is granted but the system has not bound the listener. This is what "
                    + "a force-stop leaves behind.",
                style = MaterialTheme.typography.bodySmall,
                color = Faint,
            )
            Spacer(Modifier.height(8.dp))
            TapText("Request rebind") {
                NotificationListenerService.requestRebind(
                    ComponentName(ctx, NotifListener::class.java),
                )
            }
        }

        SectionHeader("PREVIEW")
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .border(1.dp, RuleGrey),
        ) {
            DotField(dots = dots, showClock = clock, jitterMillis = 4_000L)
        }
        Spacer(Modifier.height(10.dp))
        TapText("Test glance now") { GlanceController.show(ctx, Trigger.TEST) }

        SectionHeader("MODE")
        ModeRow("Off", "Listener stays bound, nothing lights up.", mode == Mode.OFF) {
            mode = Mode.OFF; prefs.mode = mode
        }
        ModeRow("Poke", "Wake briefly on arrival, and on lift.", mode == Mode.POKE) {
            mode = Mode.POKE; prefs.mode = mode
        }
        ModeRow("Always", "Panel held on whenever the phone would sleep. Expect 2-4%/hr.", mode == Mode.ALWAYS) {
            mode = Mode.ALWAYS; prefs.mode = mode
        }

        SectionHeader("BEHAVIOUR")
        ToggleRow("Lift to show", lift) { lift = it; prefs.liftToShow = it }
        ToggleRow("Show clock", clock) { clock = it; prefs.showClock = it }
        StepperRow("Dwell", "${dwell}s") { up ->
            dwell = (dwell + if (up) 2 else -2).coerceIn(3, 30); prefs.dwellSeconds = dwell
        }
        StepperRow("Brightness", "${(bright * 100).toInt()}%") { up ->
            bright = (bright + if (up) 0.01f else -0.01f).coerceIn(0.01f, 0.20f)
            prefs.brightness = bright
        }

        SectionHeader("SOURCES")
        val assigned = remember(tick) { prefs.assignedPackages().toList().sortedBy { it.second } }
        if (assigned.isEmpty()) {
            Text(
                "Nothing seen yet. Sources appear here the first time they post.",
                style = MaterialTheme.typography.bodySmall,
                color = Faint,
            )
        }
        assigned.forEach { (pkg, slot) ->
            SourceRow(pkg, slot, dots.firstOrNull { it.pkg == pkg }?.count ?: 0)
        }
        Spacer(Modifier.height(10.dp))
        TapText("Reset slot assignments") {
            prefs.forgetSlots()
            Toast.makeText(ctx, "Slots cleared", Toast.LENGTH_SHORT).show()
        }

        SectionHeader("LAST DECISION")
        Text(lastDecision, style = MaterialTheme.typography.bodySmall, color = Faint, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Spacer(Modifier.height(26.dp))
    Text(text, style = MaterialTheme.typography.labelSmall, color = Dim)
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(RuleGrey))
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Color.White)
        Text(
            if (ok) "granted" else "missing",
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) Color.White else Dim,
        )
    }
}

@Composable
private fun CommandBox(cmd: String) {
    val ctx = LocalContext.current
    Spacer(Modifier.height(8.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .border(1.dp, RuleGrey)
            .clickable {
                ctx.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("adb", cmd))
                Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
            }
            .padding(12.dp),
    ) {
        Text(cmd, fontSize = 11.sp, color = Dim, fontFamily = FontFamily.Monospace)
    }
    Spacer(Modifier.height(4.dp))
    Text("Tap to copy. Run it from a computer.", fontSize = 11.sp, color = Faint)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ModeRow(title: String, blurb: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(13.dp).drawBehind {
                val r = size.minDimension / 2f
                if (selected) {
                    drawCircle(Color.White, radius = r)
                } else {
                    drawCircle(Faint, radius = r - 1.dp.toPx() / 2f, style = Stroke(1.dp.toPx()))
                }
            },
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(blurb, style = MaterialTheme.typography.bodySmall, color = Faint)
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Color.White)
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Dim,
                uncheckedTrackColor = Color.Black,
                uncheckedBorderColor = Dim,
            ),
        )
    }
}

@Composable
private fun StepperRow(label: String, value: String, onStep: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Color.White)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TapText("−") { onStep(false) }
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = Dim,
                modifier = Modifier.width(64.dp).padding(horizontal = 8.dp),
            )
            TapText("+") { onStep(true) }
        }
    }
}

@Composable
private fun SourceRow(pkg: String, slot: Int, count: Int) {
    val spec = SlotMap.spec(pkg)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(13.dp).drawBehind { drawGlyph(spec.glyph, Color.White) })
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(spec.label, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(pkg, fontSize = 10.sp, color = Faint, fontFamily = FontFamily.Monospace)
        }
        Text(
            if (count > 0) "slot $slot  ·  $count" else "slot $slot",
            style = MaterialTheme.typography.bodySmall,
            color = Dim,
        )
    }
}

@Composable
private fun TapText(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White,
        modifier = Modifier
            .border(1.dp, RuleGrey)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}
