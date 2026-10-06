package de.flexy.stundenplan.desktop

/** Autostart über HKCU\...\Run (kein Admin nötig). Startet minimiert im Infobereich. */
object Autostart {
    private const val KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val NAME = "HKA Stundenplan"

    val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")

    /** Pfad zur gestarteten .exe (bei der installierten App), sonst null (z.B. beim Entwickeln). */
    private fun exePath(): String? =
        ProcessHandle.current().info().command().orElse(null)
            ?.takeIf { it.endsWith(".exe", ignoreCase = true) && !it.endsWith("java.exe", ignoreCase = true) }

    val available: Boolean get() = isWindows && exePath() != null

    fun set(enabled: Boolean): Boolean {
        if (!isWindows) return false
        val cmd = if (enabled) {
            val exe = exePath() ?: return false
            listOf("reg", "add", KEY, "/v", NAME, "/t", "REG_SZ", "/d", "\"$exe\" --minimized", "/f")
        } else {
            listOf("reg", "delete", KEY, "/v", NAME, "/f")
        }
        return runCatching {
            ProcessBuilder(cmd).redirectErrorStream(true).start().waitFor() == 0
        }.getOrDefault(false)
    }
}
