package com.idealplayer.app.core.common

import kotlinx.coroutines.CancellationException

/** Keeps broad I/O error handling from converting coroutine cancellation into a failure result. */
fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}
