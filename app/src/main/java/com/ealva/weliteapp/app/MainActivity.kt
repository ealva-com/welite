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

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.unaryPlus
import com.ealva.welite.db.Database
import com.ealva.welite.db.expr.like
import com.ealva.welite.db.javatime.localDate
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.db.table.Table
import com.ealva.weliteapp.app.MediaFileTable.fileName
import com.ealva.weliteapp.app.MediaFileTable.localDate
import com.ealva.weliteapp.app.MediaFileTable.mediaTitle
import kotlinx.coroutines.Dispatchers
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

private val LOG by lazyLogger(MainActivity::class)

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
          it[mediaUri].bindParam()
          it[fileName].bindParam()
          it[mediaTitle].bindParam()
          it[localDate] = LocalDate.now()
        }

        insertStatement.insert {
          it[0] = "file.mpg"
          it[1] = "/dir/Music/file.mpg"
          it[2] = "A Title"
        }

        insertStatement.insert {
          it[0] = "/dir/Music/anotherFile.mpg"
          it[1] = "anotherFile.mpg"
          it[2] = "Another Title"
        }

        MediaFileTable.insert(OnConflict.Ignore) {
          it[mediaUri] = "/dir/Music/third.mp3"
          it[fileName] = "third"
          it[mediaTitle] = "Third Title"
        }

        MediaFileTable.insert(OnConflict.Ignore, { it[0] = "/dir/Music/fourth.mp3" }) {
          it[mediaUri].bindParam()
          it[fileName] = "fourth"
          it[mediaTitle] = "Fourth Title"
        }

        setSuccessful()
      }

      db.query {
        val count = MediaFileTable.select(fileName).where { mediaTitle like "%Title%" }.count()
        launch(Dispatchers.Main) { LOG.i { it("count=%d", count) } }

        MediaFileTable
          .select(mediaTitle, localDate)
          .where { mediaTitle like "%Title%" }
          .flow { Pair(it[mediaTitle], it[localDate]) }
          .collect { (title, date) -> LOG.i { +it("collect %s %s", title, date) } }

        MediaFileTable
          .select(mediaTitle, localDate)
          .where { mediaTitle like "%Title%" }
          .sequence { Pair(it[mediaTitle], it[localDate]) }
          .forEach { (title, date) ->
            LOG.i { +it("forEach %s %s", title, date) }
          }
      }
    }
  }
}
