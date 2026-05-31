package xdman.util

import java.io.PrintStream

class Logger {
    companion object {
        private fun getLogStream(): PrintStream = System.out
        private fun getErrorStream(): PrintStream = System.err

        @JvmStatic
        fun log(obj: Any?) {
            if (obj is Throwable) {
                getErrorStream().print("[ " + Thread.currentThread().name + " ] ")
                obj.printStackTrace(getErrorStream())
            } else {
                getLogStream().println("[ " + Thread.currentThread().name + " ] " + obj)
            }
        }
    }
}
