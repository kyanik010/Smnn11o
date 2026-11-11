package com.idealplayer.app.core.audio

import android.util.Log

/**
 * Defensive logging utility that safely writes to Logcat in Android/Robolectric
 * environments without throwing "Method ... not mocked" in pure JVM environments.
 */
internal object AudioLog {
    fun d(tag: String, message: String) {
        try {
            Log.d(tag, message)
        } catch (_: Throwable) {
            println("DEBUG [$tag]: $message")
        }
    }

    fun i(tag: String, message: String) {
        try {
            Log.i(tag, message)
        } catch (_: Throwable) {
            println("INFO [$tag]: $message")
        }
    }

    fun w(tag: String, message: String, tr: Throwable? = null) {
        try {
            if (tr != null) Log.w(tag, message, tr) else Log.w(tag, message)
        } catch (_: Throwable) {
            println("WARN [$tag]: $message ${tr?.message ?: ""}")
        }
    }

    fun e(tag: String, message: String, tr: Throwable? = null) {
        try {
            if (tr != null) Log.e(tag, message, tr) else Log.e(tag, message)
        } catch (_: Throwable) {
            println("ERROR [$tag]: $message ${tr?.message ?: ""}")
        }
    }
}
