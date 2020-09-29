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

package com.ealva.weliteapp.appdb

import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.welite.db.Database
import com.ealva.welite.db.OpenParams
import com.ealva.welite.db.journalMode
import com.ealva.welite.db.log.WeLiteLog
import com.ealva.welite.db.synchronousMode
import com.ealva.weliteapp.app.MediaFileTable
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val LOG by lazyLogger("Db.kt", WeLiteLog.marker)

val dbModule = module {
  single {
    Database(
      context = androidContext(),
      fileName = "WeLiteTest",
      tables = listOf(MediaFileTable),
      version = 1,
      openParams = OpenParams(
        enableWriteAheadLogging = true,
        enableForeignKeyConstraints = true
      ),
      configure = {
        onConfigure { configuration ->
          LOG.i { it("onConfigure") }
          LOG.i { it("    JournalMode=%s", configuration.journalMode) }
          LOG.i { it("    SynchronousMode=%s", configuration.synchronousMode) }
          LOG.i { it("    maxSize=%d", configuration.maximumSize) }
        }
        onCreate { database ->
          LOG.i { it("onCreate %d", database.tables.size) }
        }
        onOpen { database ->
          LOG.i { it("onOpen %d", database.tables.size) }
        }
      }
    )
  }
}
