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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.welite.db.Database
import com.ealva.welite.db.expr.bindString
import com.ealva.welite.db.expr.like
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.db.table.Table
import com.ealva.weliteapp.app.MediaFileTable.fileName
import com.ealva.weliteapp.app.MediaFileTable.mediaTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

object MediaFileTable : Table() {
  val id = integer("_id") { primaryKey() }
  val mediaUri = text("MediaUri") { notNull().unique() }
  val fileName = text("MediaFileName") { notNull().collateNoCase() }
  val mediaTitle = text("MediaTitle") { notNull().collateNoCase() }
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
        check(MediaFileTable.exists)  // Was table created

        LOG.i { it("insert into MediaFileTable") }

        // ignore conflict because we're calling this during onCreate
        val insertStatement = MediaFileTable.insertValues(OnConflict.Ignore) {
          it[mediaUri] = bindString()
          it[fileName] = bindString()
          it[mediaTitle] = bindString()
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
          it[mediaUri] = bindString()
          it[fileName] = "fourth"
          it[mediaTitle] = "Fourth Title"
        }

        LOG.i { it("Mark txn successful") }
        setSuccessful()
      }

      db.query {
        val count = MediaFileTable.select(fileName).where { mediaTitle like "%Title%" }.count()
        launch(Dispatchers.Main) {
          Toast.makeText(this@MainActivity, "$count", Toast.LENGTH_SHORT).show()
        }

        MediaFileTable.select(mediaTitle).where { mediaTitle like "%Title%" }.forEach {
          val title = it[mediaTitle]
          launch {
            Toast.makeText(this@MainActivity, title, Toast.LENGTH_SHORT).show()
          }
        }
      }
    }
    LOG.i { it("Txn closed") }
  }
}
