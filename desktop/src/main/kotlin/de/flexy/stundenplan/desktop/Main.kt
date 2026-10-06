package de.flexy.stundenplan.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import de.flexy.stundenplan.desktop.ui.App
import de.flexy.stundenplan.desktop.ui.NavController
import de.flexy.stundenplan.desktop.ui.MiniView
import de.flexy.stundenplan.desktop.ui.StundenplanTheme
import de.flexy.stundenplan.desktop.ui.trayTooltip
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.time.LocalDateTime
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import javax.imageio.ImageIO
import kotlin.system.exitProcess

private val showRequest = AppDirs.file("show.request")

/** Nur eine Instanz: läuft schon eine, wird diese nach vorne geholt. */
private fun acquireSingleInstance(): FileLock? = runCatching {
    RandomAccessFile(AppDirs.file("app.lock"), "rw").channel.tryLock()
}.getOrNull()

fun main(args: Array<String>) {
    val lock = acquireSingleInstance()
    if (lock == null) {
        runCatching { showRequest.writeText(System.currentTimeMillis().toString()) }
        exitProcess(0)
    }
    val startMinimized = "--minimized" in args

    val iconImage = Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")
        ?.use { ImageIO.read(it) }?.toComposeImageBitmap()

    application {
        val icon = remember { iconImage?.let { BitmapPainter(it) } }
        val trayState = rememberTrayState()
        val scope = rememberCoroutineScope()
        val model = remember {
            AppModel(scope) { title, text -> trayState.sendNotification(Notification(title, text, Notification.Type.Info)) }
        }
        val nav = remember { NavController() }
        var visible by remember { mutableStateOf(!startMinimized) }
        var hintShown by remember { mutableStateOf(false) }
        val state by model.state.collectAsState()
        val now by produceState(LocalDateTime.now()) {
            while (true) {
                delay(30_000)
                value = LocalDateTime.now()
            }
        }
        val windowState = remember {
            val s = model.state.value.settings
            val saved = s.winW >= 400 && s.winH >= 300 && onScreen(s.winX, s.winY, s.winW)
            WindowState(
                placement = if (s.winMax) WindowPlacement.Maximized else WindowPlacement.Floating,
                position = if (saved) WindowPosition(s.winX.dp, s.winY.dp) else WindowPosition(Alignment.Center),
                size = if (saved) DpSize(s.winW.dp, s.winH.dp) else DpSize(1280.dp, 820.dp),
            )
        }
        val miniState = remember {
            val s = model.state.value.settings
            WindowState(
                position = if (s.miniX >= 0 && onScreen(s.miniX, s.miniY, 320)) WindowPosition(s.miniX.dp, s.miniY.dp)
                else WindowPosition(Alignment.BottomEnd),
                size = DpSize(340.dp, 190.dp),
            )
        }
        val dark = when (state.settings.themeMode) {
            "light" -> false
            "dark" -> true
            else -> isSystemInDarkTheme()
        }
        val showMain = {
            visible = true
            windowState.isMinimized = false
        }

        // Fenstergröße/-position merken
        LaunchedEffect(Unit) {
            snapshotFlow { Triple(windowState.placement, windowState.position, windowState.size) }
                .collectLatest { (placement, pos, size) ->
                    delay(800)
                    if (windowState.isMinimized) return@collectLatest
                    val max = placement == WindowPlacement.Maximized
                    if (max) model.saveWindow(0, 0, 0, 0, true)
                    else if (placement == WindowPlacement.Floating && pos is WindowPosition.Absolute) {
                        model.saveWindow(pos.x.value.toInt(), pos.y.value.toInt(), size.width.value.toInt(), size.height.value.toInt(), false)
                    }
                }
        }
        LaunchedEffect(Unit) {
            snapshotFlow { miniState.position }.collectLatest { pos ->
                delay(800)
                if (pos is WindowPosition.Absolute) model.saveMiniPosition(pos.x.value.toInt(), pos.y.value.toInt())
            }
        }

        // Zweite Instanz gestartet → Fenster zeigen
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                if (showRequest.exists()) {
                    showRequest.delete()
                    visible = true
                    windowState.isMinimized = false
                }
            }
        }

        if (icon != null) {
            Tray(
                icon = icon,
                state = trayState,
                tooltip = trayTooltip(state.visible, now),
                onAction = showMain,
                menu = {
                    Item("Öffnen", onClick = showMain)
                    CheckboxItem("Mini-Fenster", checked = state.settings.miniWindow, onCheckedChange = { model.setMiniWindow(it) })
                    Item("Aktualisieren", onClick = { model.refresh() })
                    Separator()
                    Item("Beenden", onClick = {
                        lock.release()
                        exitApplication()
                    })
                },
            )
        }

        Window(
            onCloseRequest = {
                // Schließen = in den Infobereich, damit Erinnerungen weiter ankommen
                if (icon != null) {
                    visible = false
                    if (!hintShown) {
                        hintShown = true
                        trayState.sendNotification(
                            Notification("Stundenplan läuft weiter", "Zum Öffnen auf das Symbol im Infobereich klicken. Beenden über Rechtsklick.")
                        )
                    }
                } else {
                    exitApplication()
                }
            },
            visible = visible,
            state = windowState,
            title = "HKA Stundenplan",
            icon = icon,
            onPreviewKeyEvent = { e ->
                if (e.type != KeyEventType.KeyDown) return@Window false
                when {
                    e.key == Key.DirectionLeft && !nav.typing() -> { nav.step(-1); true }
                    e.key == Key.DirectionRight && !nav.typing() -> { nav.step(1); true }
                    e.key == Key.Escape -> nav.closeOverlay()
                    e.key == Key.T && e.isCtrlPressed -> { nav.today(); true }
                    (e.key == Key.R && e.isCtrlPressed) || e.key == Key.F5 -> { model.refresh(); true }
                    e.key == Key.M && e.isCtrlPressed -> { model.setMiniWindow(!state.settings.miniWindow); true }
                    else -> false
                }
            },
        ) {
            LaunchedEffect(visible) { if (visible) window.toFront() }
            StundenplanTheme(dark = dark) {
                App(model, nav)
            }
        }

        if (state.settings.miniWindow) {
            Window(
                onCloseRequest = { model.setMiniWindow(false) },
                state = miniState,
                title = "Als Nächstes",
                icon = icon,
                alwaysOnTop = true,
                resizable = true,
                onPreviewKeyEvent = { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.M && e.isCtrlPressed) { model.setMiniWindow(false); true } else false
                },
            ) {
                StundenplanTheme(dark = dark) {
                    MiniView(state.visible, now, onOpen = showMain)
                }
            }
        }
    }
}

/** Liegt die gespeicherte Fensterposition noch auf einem vorhandenen Bildschirm? */
private fun onScreen(x: Int, y: Int, w: Int): Boolean = runCatching {
    val title = Rectangle(x + 40, y, (w - 80).coerceAtLeast(40), 30)
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any {
        it.defaultConfiguration.bounds.intersects(title)
    }
}.getOrDefault(false)
