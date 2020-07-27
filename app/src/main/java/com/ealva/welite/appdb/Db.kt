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

package com.ealva.welite.appdb

import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.welite.app.MediaFileTable
import com.ealva.welite.db.Database
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val LOG by lazyLogger("Db.kt")

val dbModule = module {
  single {
    Database(
      context = androidContext(),
      fileName = "WeLiteTest",
      version = 1,
      tables = listOf(MediaFileTable),
      migrations = emptyList()
    ) {
      preOpen { params ->
        LOG.i { it("preOpen") }
        params.enableWriteAheadLogging(true)
      }
      onConfigure { configuration ->
        LOG.i { it("onConfigure") }
        configuration.enableForeignKeyConstraints(true)
        configuration.execPragma("synchronous=NORMAL")
      }
//      onCreate { LOG.i { it("onCreate") } }
//      onOpen { LOG.i { it("onOpen") } }
    }
  }
}
