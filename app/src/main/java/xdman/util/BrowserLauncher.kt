package xdman.util

import java.io.File

class BrowserLauncher {
    companion object {
        @JvmStatic
        fun launchFirefox(args: String?): Boolean {
            val os = XDMUtils.detectOS()
            if (os == XDMUtils.WINDOWS) {
                val ffPaths = arrayOf(
                    File(System.getenv("ProgramW6432") ?: "", "Mozilla Firefox\\firefox.exe"),
                    File(System.getenv("PROGRAMFILES") ?: "", "Mozilla Firefox\\firefox.exe"),
                    File(System.getenv("PROGRAMFILES(X86)") ?: "", "Mozilla Firefox\\firefox.exe")
                )
                for (path in ffPaths) {
                    println(path)
                    if (path.exists()) {
                        return XDMUtils.exec("\"$path\" $args")
                    }
                }
            }
            if (os == XDMUtils.MAC) {
                val ffPaths = arrayOf(File("/Applications/Firefox.app"))
                for (path in ffPaths) {
                    if (path.exists()) {
                        return MacUtils.launchApp(path.absolutePath, args)
                    }
                }
            }
            if (os == XDMUtils.LINUX) {
                val ffPaths = arrayOf(File("/usr/bin/firefox"))
                for (path in ffPaths) {
                    if (path.exists()) {
                        return XDMUtils.exec("$path $args")
                    }
                }
            }
            return false
        }

        @JvmStatic
        fun launchChrome(args: String?): Boolean {
            val os = XDMUtils.detectOS()
            if (os == XDMUtils.WINDOWS) {
                val ffPaths = arrayOf(
                    File(System.getenv("PROGRAMFILES") ?: "", "Google\\Chrome\\Application\\chrome.exe"),
                    File(System.getenv("PROGRAMFILES(X86)") ?: "", "Google\\Chrome\\Application\\chrome.exe"),
                    File(System.getenv("LOCALAPPDATA") ?: "", "Google\\Chrome\\Application\\chrome.exe")
                )
                for (path in ffPaths) {
                    if (path.exists()) {
                        return XDMUtils.exec("\"$path\" $args")
                    }
                }
            }
            if (os == XDMUtils.MAC) {
                val ffPaths = arrayOf(File("/Applications/Google Chrome.app"))
                for (path in ffPaths) {
                    if (path.exists()) {
                        return MacUtils.launchApp(path.absolutePath, args)
                    }
                }
            }
            if (os == XDMUtils.LINUX) {
                val ffPaths = arrayOf(File("/usr/bin/google-chrome"))
                for (path in ffPaths) {
                    if (path.exists()) {
                        return XDMUtils.exec("$path $args")
                    }
                }
            }
            return false
        }
    }
}
