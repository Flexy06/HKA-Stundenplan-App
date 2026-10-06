package de.flexy.stundenplan.desktop

import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import de.flexy.stundenplan.desktop.ui.App
import de.flexy.stundenplan.desktop.ui.NavController
import de.flexy.stundenplan.desktop.ui.StundenplanTheme
import kotlinx.coroutines.delay
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
        val windowState = rememberWindowState(size = DpSize(1280.dp, 820.dp), position = WindowPosition(Alignment.Center))

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
                tooltip = "HKA Stundenplan",
                onAction = {
                    visible = true
                    windowState.isMinimized = false
                },
                menu = {
                    Item("Öffnen", onClick = {
                        visible = true
                        windowState.isMinimized = false
                    })
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
                    else -> false
                }
            },
        ) {
            LaunchedEffect(visible) { if (visible) window.toFront() }
            StundenplanTheme {
                App(model, nav)
            }
        }
    }
}
