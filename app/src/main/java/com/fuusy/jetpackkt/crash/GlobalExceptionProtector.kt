package com.fuusy.jetpackkt.crash

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Global exception protector that prevents the app from crashing by catching
 * uncaught exceptions on both the main thread and background threads.
 *
 * WARNING: This masks crashes and should be used carefully.
 */
object GlobalExceptionProtector {
	private const val TAG = "GlobalExceptionProtector"
	@Volatile private var installed = false
	@Volatile private var oldHandler: Thread.UncaughtExceptionHandler? = null

	fun install(app: Application) {
		if (installed) return
		installed = true

		oldHandler = Thread.getDefaultUncaughtExceptionHandler()

		// Catch background thread crashes
		Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
			Log.e(TAG, "Uncaught exception in thread: ${thread.name}", throwable)
			// Optionally report to analytics/log file here
			// Swallow to keep process alive; avoid fatal Errors
			if (throwable is Error) {
				oldHandler?.uncaughtException(thread, throwable)
			}
		}

		// Guard the main looper to avoid app crash on UI exceptions
		Handler(Looper.getMainLooper()).post {
			while (installed) {
				try {
					Looper.loop()
				} catch (t: Throwable) {
					// Ignore but log; keep the main looper running
					Log.e(TAG, "Exception in main looper", t)
					if (t is Error) {
						// Let fatal errors crash to avoid undefined state
						throw t
					}
				}
			}
		}
	}

	fun uninstall() {
		installed = false
		oldHandler?.let { Thread.setDefaultUncaughtExceptionHandler(it) }
		oldHandler = null
	}
} 