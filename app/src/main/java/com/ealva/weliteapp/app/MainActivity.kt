/*
 * Copyright 2020 eAlva.com
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

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.unaryPlus
import com.ealva.welite.db.Database
import com.ealva.welite.db.expr.like
import com.ealva.welite.javatime.localDate
import com.ealva.welite.db.log.WeLiteLog
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.db.table.Table
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.time.LocalDate

object MediaFileTable : Table() {
  val id = integer("_id") { primaryKey() }
  val mediaUri = text("MediaUri") { unique() }
  val fileName = text("MediaFileName") { collateNoCase() }
  val mediaTitle = text("MediaTitle") { collateNoCase() }
  val localDate = localDate("local_date")
}

private val LOG by lazyLogger(MainActivity::class, WeLiteLog.marker)

class MainActivity : AppCompatActivity() {
  private val db: Database by inject()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    LOG.i { it("launch") }
    lifecycleScope.launch {
      LOG.i { it("begin txn") }

      // On first app install tables/etc will be created here when the txn requests a writeable DB
      db.transaction {
        check(MediaFileTable.exists) // Was table created

        LOG.i { it("insert into MediaFileTable") }

        // ignore conflict because we're calling this during onCreate
        val insertStatement = MediaFileTable.insertValues(OnConflict.Ignore) {
          it[mediaUri].bindArg()
          it[fileName].bindArg()
          it[mediaTitle].bindArg()
          it[localDate] = LocalDate.now()
        }

        insertStatement.insert {
          it[MediaFileTable.mediaUri] = "file.mpg"
          it[MediaFileTable.fileName] = "/dir/Music/file.mpg"
          it[MediaFileTable.mediaTitle] = "A Title"
        }

        insertStatement.insert {
          it[MediaFileTable.mediaUri] = "/dir/Music/anotherFile.mpg"
          it[MediaFileTable.fileName] = "anotherFile.mpg"
          it[MediaFileTable.mediaTitle] = "Another Title"
        }

        MediaFileTable.insert(OnConflict.Ignore) {
          it[mediaUri] = "/dir/Music/third.mp3"
          it[fileName] = "third"
          it[mediaTitle] = "Third Title"
        }

        MediaFileTable.insert(OnConflict.Ignore, { it[0] = "/dir/Music/fourth.mp3" }) {
          it[mediaUri].bindArg()
          it[fileName] = "fourth"
          it[mediaTitle] = "Fourth Title"
        }

        setSuccessful()
      }

      db.query {
        val count = MediaFileTable.select(MediaFileTable.fileName).where {
          MediaFileTable.mediaTitle like "%Title%"
        }.count()
        LOG.i { it("count=%d", count) }

        MediaFileTable
          .select(MediaFileTable.mediaTitle, MediaFileTable.localDate)
          .where { MediaFileTable.mediaTitle like "%Title%" }
          .flow { Pair(it[MediaFileTable.mediaTitle], it[MediaFileTable.localDate]) }
          .collect { (title, date) -> LOG.i { +it("collect %s %s", title, date) } }

        MediaFileTable
          .select(MediaFileTable.mediaTitle, MediaFileTable.localDate)
          .where { MediaFileTable.mediaTitle like "%Title%" }
          .sequence { Pair(it[MediaFileTable.mediaTitle], it[MediaFileTable.localDate]) }
          .forEach { (title, date) ->
            LOG.i { +it("forEach %s %s", title, date) }
          }
      }
    }
  }
}
