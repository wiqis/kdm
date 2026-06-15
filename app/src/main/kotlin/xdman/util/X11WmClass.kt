package xdman.util

import com.sun.jna.Native
import java.awt.Frame
import java.util.concurrent.TimeUnit

/**
 * Manages X11 WM_CLASS for KDM window icon association on Linux/GNOME.
 *
 * GNOME Shell reads WM_CLASS when a window appears to match it with a .desktop
 * file's StartupWMClass. This utility sets WM_CLASS to "kdm" on the window
 * so it matches the desktop entry's StartupWMClass=kdm.
 *
 * Approach:
 * - Uses JNA's Native.getWindowID() to get the X11 window ID from the AWT Frame
 *   (works with --add-opens or via JNA's native JNI helper)
 * - Uses xprop (installed by default on Ubuntu) to set WM_CLASS on the window
 * - Falls back to xprop -name "KDM 2026" if getWindowID fails
 *
 * No external dependencies required (xdotool, etc.)
 */
object X11WmClass {

    private val isLinux = System.getProperty("os.name").lowercase().contains("linux")
    private const val WINDOW_TITLE = "KDM 2026"

    /**
     * Sets WM_CLASS to "kdm" on the given Frame's X11 window.
     * Polls for the window ID if not immediately available.
     * This should be called inside the Window composable's LaunchedEffect.
     */
    fun setWmClass(frame: Frame) {
        if (!isLinux) return

        // Get the X11 window ID from the AWT Frame
        val windowId = getWindowId(frame)

        if (windowId != null) {
            setWmClassById(windowId)
        } else {
            Logger.log("X11WmClass: Could not get window ID from Frame, trying xprop -name fallback")
            setWmClassByName()
        }
    }

    /**
     * Detects the actual X11 WM_CLASS of the KDM window.
     * Returns the class part (second value, e.g. "xdman.MainKt") or null.
     * Uses xprop -name (no external tools needed).
     */
    fun detectWmClass(): String? {
        if (!isLinux) return null

        try {
            val proc = ProcessBuilder("xprop", "-name", WINDOW_TITLE, "WM_CLASS")
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().use { it.readText().trim() }
            proc.waitFor(3, TimeUnit.SECONDS)

            if (proc.exitValue() != 0 || output.isBlank()) {
                Logger.log("X11WmClass: xprop -name returned nothing for '$WINDOW_TITLE'")
                return null
            }

            // Parse: WM_CLASS(STRING) = "res_name", "res_class"
            val match = Regex("""WM_CLASS\(STRING\)\s*=\s*"([^"]*)",\s*"([^"]*)" """.trimEnd()).find(output)
            if (match != null) {
                val resClass = match.groupValues[2]
                Logger.log("X11WmClass: Detected WM_CLASS = \"${match.groupValues[1]}\", \"$resClass\"")
                return resClass
            }

            Logger.log("X11WmClass: Could not parse WM_CLASS from: $output")
            return null
        } catch (e: Exception) {
            Logger.log("X11WmClass: detect failed: ${e.message}")
            return null
        }
    }

    /**
     * Gets the X11 window ID from a Frame using JNA's Native.getWindowID().
     * This accesses the AWT peer internally via JNA's native helper.
     */
    private fun getWindowId(frame: Frame): Long? {
        try {
            val id = Native.getWindowID(frame)
            if (id != 0L) {
                Logger.log("X11WmClass: Got window ID $id from JNA")
                return id
            }
        } catch (e: Exception) {
            Logger.log("X11WmClass: JNA getWindowID failed: ${e.message}")
        }
        return null
    }

    /**
     * Sets WM_CLASS to "kdm" on the given X11 window ID using xprop.
     */
    private fun setWmClassById(windowId: Long) {
        try {
            val proc = ProcessBuilder(
                "xprop", "-id", windowId.toString(),
                "-f", "WM_CLASS", "8s",
                "-set", "WM_CLASS", "kdm"
            ).inheritIO().start()

            val exited = proc.waitFor(3, TimeUnit.SECONDS)
            if (exited && proc.exitValue() == 0) {
                Logger.log("X11WmClass: Set WM_CLASS to kdm on window $windowId")
            } else {
                Logger.log("X11WmClass: xprop -id failed (exit: ${proc.exitValue()})")
            }
        } catch (e: Exception) {
            Logger.log("X11WmClass: setWmClassById failed: ${e.message}")
        }
    }

    /**
     * Fallback: sets WM_CLASS using xprop -name to find the window by title.
     */
    private fun setWmClassByName() {
        try {
            val proc = ProcessBuilder(
                "xprop", "-name", WINDOW_TITLE,
                "-f", "WM_CLASS", "8s",
                "-set", "WM_CLASS", "kdm"
            ).inheritIO().start()

            val exited = proc.waitFor(3, TimeUnit.SECONDS)
            if (exited && proc.exitValue() == 0) {
                Logger.log("X11WmClass: Set WM_CLASS via xprop -name '$WINDOW_TITLE'")
            } else {
                Logger.log("X11WmClass: xprop -name failed (exit: ${proc.exitValue()})")
            }
        } catch (e: Exception) {
            Logger.log("X11WmClass: setWmClassByName failed: ${e.message}")
        }
    }
}
