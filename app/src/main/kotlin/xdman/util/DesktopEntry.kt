package xdman.util

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset

/**
 * Manages the KDM desktop entry for GNOME dock icon association.
 *
 * Strategy: detects the window's actual X11 WM_CLASS using xprop, then
 * patches the .desktop file's StartupWMClass to match. This is reliable
 * because we match the desktop entry to the window's actual class, rather
 * than trying to force the window's WM_CLASS to a specific value.
 */
object DesktopEntry {

    private const val APP_NAME = "KDM Download Manager"
    private const val DESKTOP_FILE = "kdm.desktop"
    private const val ICON_DIR_NAME = "kdm"

    /**
     * Detects the current window's WM_CLASS and patches the desktop entry.
     * Returns a message describing what was done.
     */
    fun install(): String {
        // Detect the actual WM_CLASS of the KDM window
        val detectedClass = X11WmClass.detectWmClass()
        val wmClass = detectedClass ?: "kdm"  // fallback

        // Clean up any files a previous version of this installer may have created
        cleanupLegacyFiles()

        val existingFile = findExistingEntry()

        if (existingFile != null) {
            return patchExistingEntry(existingFile, wmClass)
        }

        return createNewEntry(wmClass)
    }

    /**
     * Detects the window's actual WM_CLASS and patches the desktop entry silently.
     * Returns true if a change was made, false if no change was needed or detection failed.
     * This is called automatically on startup.
     */
    fun autoPatch(): Boolean {
        val detectedClass = X11WmClass.detectWmClass() ?: return false
        val existingFile = findExistingEntry() ?: return false

        try {
            val content = existingFile.readText()
            if (content.contains("StartupWMClass=$detectedClass")) {
                // Already correct, no change needed
                return false
            }
            // Patch it
            patchExistingEntry(existingFile, detectedClass)
            Logger.log("DesktopEntry: Auto-patched StartupWMClass=$detectedClass in ${existingFile.absolutePath}")
            return true
        } catch (e: Exception) {
            Logger.log("DesktopEntry: Auto-patch failed: ${e.message}")
            return false
        }
    }

    /**
     * Cleans up any files that a previous version of this installer may have created,
     * to prevent duplicate entries.
     */
    fun cleanupLegacyFiles() {
        val localDesktop = File(getApplicationsDir(), DESKTOP_FILE)
        if (localDesktop.exists()) {
            localDesktop.delete()
            Logger.log("Removed legacy desktop entry: ${localDesktop.absolutePath}")
        }

        val iconDir = File(File(System.getProperty("user.home"), ".local/share"), ICON_DIR_NAME)
        if (iconDir.exists()) {
            iconDir.listFiles()?.forEach { it.delete() }
            iconDir.delete()
            Logger.log("Removed legacy icon directory: ${iconDir.absolutePath}")
        }
    }

    /**
     * Searches for existing KDM .desktop files in standard locations.
     * Returns the first one found, or null if none exists.
     */
    private fun findExistingEntry(): File? {
        val searchDirs = listOf(
            getApplicationsDir(),                    // ~/.local/share/applications/
            File("/usr/share/applications"),         // system-wide
            File("/usr/local/share/applications"),   // local system
        )

        val searchPatterns = listOf("kdm.desktop", "kdm-kdm.desktop", "xdm.desktop", "xdm-xdm.desktop")

        for (dir in searchDirs) {
            if (!dir.isDirectory) continue
            for (pattern in searchPatterns) {
                val file = File(dir, pattern)
                if (file.exists()) {
                    Logger.log("Found existing desktop entry: ${file.absolutePath}")
                    return file
                }
            }
        }

        return null
    }

    /**
     * Patches an existing .desktop file with StartupWMClass=<detected class>.
     * If the file is in a system directory (not writable), copies it to
     * ~/.local/share/applications/ first (which overrides system entries).
     */
    private fun patchExistingEntry(desktopFile: File, wmClass: String): String {
        try {
            val content = desktopFile.readText()

            if (content.contains("StartupWMClass=$wmClass")) {
                return "Desktop entry already has StartupWMClass=$wmClass:\n${desktopFile.absolutePath}\n\n" +
                        "No changes needed."
            }

            val updatedContent = when {
                content.contains("StartupWMClass=") -> {
                    content.replace(Regex("StartupWMClass=.*"), "StartupWMClass=$wmClass")
                }
                content.endsWith("\n") -> {
                    content + "StartupWMClass=$wmClass\n"
                }
                else -> {
                    content + "\nStartupWMClass=$wmClass\n"
                }
            }

            // If the file is in a system directory (not writable), write to user-local instead
            val targetFile = if (!desktopFile.canWrite()) {
                val userDir = getApplicationsDir()
                userDir.mkdirs()
                val userFile = File(userDir, desktopFile.name)
                userFile.writeText(updatedContent, Charset.forName("UTF-8"))
                userFile
            } else {
                desktopFile.writeText(updatedContent, Charset.forName("UTF-8"))
                desktopFile
            }

            Logger.log("Patched desktop entry with StartupWMClass=$wmClass: ${targetFile.absolutePath}")

            return "Desktop entry updated with StartupWMClass=$wmClass!\n\n" +
                    "Location: ${targetFile.absolutePath}\n\n" +
                    "Restart KDM for the icon to appear.\n" +
                    "If the cog icon still shows, run the menu item again after KDM is running."

        } catch (e: Exception) {
            Logger.log("Failed to patch desktop file: ${e.message}")
            return "Failed to update desktop entry: ${e.message}"
        }
    }

    /**
     * Creates a brand new desktop entry at ~/.local/share/applications/kdm.desktop
     * Only called if no existing entry is found.
     */
    private fun createNewEntry(wmClass: String): String {
        val applicationsDir = getApplicationsDir()
        applicationsDir.mkdirs()

        val iconFile = installIcon()
        val execPath = resolveExecutablePath()
        val desktopFile = File(applicationsDir, DESKTOP_FILE)

        try {
            val content = buildString {
                appendLine("[Desktop Entry]")
                appendLine("Type=Application")
                appendLine("Version=1.0")
                appendLine("Name=$APP_NAME")
                appendLine("Comment=Download Manager")
                appendLine("Exec=\"$execPath\"")
                if (iconFile != null) {
                    appendLine("Icon=${iconFile.absolutePath}")
                }
                appendLine("Terminal=false")
                appendLine("Categories=Network;FileTransfer;")
                appendLine("StartupNotify=true")
                appendLine("StartupWMClass=$wmClass")
            }

            desktopFile.writeText(content, Charset.forName("UTF-8"))

            Logger.log("Created desktop entry with StartupWMClass=$wmClass: ${desktopFile.absolutePath}")
            return "Desktop entry created with StartupWMClass=$wmClass!\n\n" +
                    "Location: ${desktopFile.absolutePath}\n\n" +
                    "Restart KDM for the icon to appear.\n" +
                    "If the cog icon still shows, run the menu item again after KDM is running."

        } catch (e: Exception) {
            Logger.log("Failed to create desktop file: ${e.message}")
            return "Failed to create desktop entry: ${e.message}"
        }
    }

    private fun getApplicationsDir(): File {
        val xdgDataHome = System.getenv("XDG_DATA_HOME")
        val baseDir = if (xdgDataHome != null) {
            File(xdgDataHome)
        } else {
            File(System.getProperty("user.home"), ".local/share")
        }
        return File(baseDir, "applications")
    }

    private fun installIcon(): File? {
        val dataDir = File(File(System.getProperty("user.home"), ".local/share"), ICON_DIR_NAME)
        dataDir.mkdirs()
        val outFile = File(dataDir, "icon-512.png")

        try {
            val stream = javaClass.getResourceAsStream("/icons/icon_512.png")
                ?: javaClass.classLoader.getResourceAsStream("icons/icon_512.png")
                ?: javaClass.classLoader.getResourceAsStream("icons/icon_256.png")

            if (stream != null) {
                stream.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                return outFile
            }
        } catch (e: Exception) {
            Logger.log("Failed to extract icon: ${e.message}")
        }

        // Fallback: look for icon in common install locations
        val commonPaths = listOf(
            "/usr/share/icons/hicolor/512x512/apps/kdm.png",
            "/usr/share/icons/hicolor/256x256/apps/kdm.png",
            "/usr/share/pixmaps/kdm.png",
            "/opt/kdm/lib/kdm.png"
        )
        for (path in commonPaths) {
            val f = File(path)
            if (f.exists()) {
                f.copyTo(outFile, overwrite = true)
                return outFile
            }
        }

        return null
    }

    private fun resolveExecutablePath(): String {
        // 1. If installed via .deb at known paths
        for (path in listOf("/usr/bin/kdm", "/opt/kdm/bin/kdm")) {
            if (File(path).exists()) return path
        }

        // 2. Check if kdm is in PATH
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("which", "kdm"))
            if (proc.waitFor() == 0) {
                val path = String(proc.inputStream.readAllBytes()).trim()
                if (path.isNotBlank() && File(path).exists()) return path
            }
        } catch (_: Exception) {}

        // 3. Fallback
        return "kdm"
    }
}
