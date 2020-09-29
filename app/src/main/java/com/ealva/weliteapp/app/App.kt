/*
 * Copyright 2020 Eric A. Snell
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ealva.weliteapp.app

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.SparseArray
import com.ealva.ealvalog.Loggers
import com.ealva.ealvalog.android.AndroidLogger
import com.ealva.ealvalog.android.AndroidLoggerFactory
import com.ealva.ealvalog.android.DebugLogHandler
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.welite.db.Database
import com.ealva.welite.db.log.WeLiteLog
import com.ealva.weliteapp.appdb.dbModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

private val levelToNameMap: SparseArray<String> = SparseArray<String>().apply {
  put(ComponentCallbacks2.TRIM_MEMORY_COMPLETE, "TRIM_MEMORY_COMPLETE")
  put(ComponentCallbacks2.TRIM_MEMORY_MODERATE, "TRIM_MEMORY_MODERATE")
  put(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND, "TRIM_MEMORY_BACKGROUND")
  put(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, "TRIM_MEMORY_UI_HIDDEN")
  put(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, "TRIM_MEMORY_RUNNING_CRITICAL")
  put(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, "TRIM_MEMORY_RUNNING_LOW")
  put(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE, "TRIM_MEMORY_RUNNING_MODERATE")
}

@Suppress("unused") // ?? warning yet search yields uses
class App : Application() {
  private val logger by lazyLogger(WeLiteLog.marker)

  override fun onCreate() {
    super.onCreate()
    setupLogging()

    startKoin {
      androidContext(this@App)
      modules(dbModule)
    }
  }

  private fun setupLogging() {
    AndroidLogger.setHandler(DebugLogHandler())
    Loggers.setFactory(AndroidLoggerFactory)
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    logger.i { it(levelToNameMap[level] ?: "onTrimMemory: Unrecognized level") }
    // Determine which lifecycle or system event was raised.
    when (level) {
      ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
        /*
           Release any UI objects that currently hold memory.

           The user interface has moved to the background.
        */
      }

      ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
      ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
      ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
        /*
           Release any memory that your app doesn't need to run.

           The device is running low on memory while the app is running.
           The event raised indicates the severity of the memory-related event.
           If the event is TRIM_MEMORY_RUNNING_CRITICAL, then the system will
           begin killing background processes.
        */
        Database.releaseMemory()
      }

      ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
      ComponentCallbacks2.TRIM_MEMORY_MODERATE,
      ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
        /*
           Release as much memory as the process can.

           The app is on the LRU list and the system is running low on memory.
           The event raised indicates where the app sits within the LRU list.
           If the event is TRIM_MEMORY_COMPLETE, the process will be one of
           the first to be terminated.
        */
        Database.releaseMemory()
      }

      else -> {
        /*
          Release any non-critical data structures.

          The app received an unrecognized memory level value
          from the system. Treat this as a generic low-memory message.
        */
      }
    }
  }
}
