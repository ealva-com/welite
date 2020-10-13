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

package com.ealva.welite.test.db

import android.content.Context
import androidx.core.net.toUri
import com.ealva.welite.db.Database
import com.ealva.welite.db.ForeignKeyInfo
import com.ealva.welite.db.ForeignKeyViolation
import com.ealva.welite.db.table.ForeignKeyAction
import com.ealva.welite.test.shared.AlbumTable
import com.ealva.welite.test.shared.ArtistAlbumTable
import com.ealva.welite.test.shared.ArtistTable
import com.ealva.welite.test.shared.MEDIA_TABLES
import com.ealva.welite.test.shared.MediaFileTable
import com.ealva.welite.test.shared.expectMediaTablesExist
import com.ealva.welite.test.shared.withTestDatabase
import com.nhaarman.expect.expect
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File

public object CommonForeignKeySchemaTests {
  public suspend fun testForeignKeyList(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, MEDIA_TABLES, testDispatcher, enableForeignKeyConstraints = false) {
      createTablesWithFKViolations()

      query {
        ArtistAlbumTable.foreignKeyList.let { list ->
          expect(list).toHaveSize(2)
          expect(list[0]).toBe(
            ForeignKeyInfo(
              id = 0,
              seq = 0,
              table = "Album",
              from = "AlbumId",
              to = "_id",
              onUpdate = ForeignKeyAction.NO_ACTION,
              onDelete = ForeignKeyAction.NO_ACTION
            )
          )
          expect(list[1]).toBe(
            ForeignKeyInfo(
              id = 1,
              seq = 0,
              table = "Artist",
              from = "ArtistId",
              to = "_id",
              onUpdate = ForeignKeyAction.NO_ACTION,
              onDelete = ForeignKeyAction.NO_ACTION
            )
          )
        }
      }
    }
  }

  private suspend fun Database.createTablesWithFKViolations() {
    expectMediaTablesExist()
    transaction {
      MediaFileTable.insert {
        it[mediaTitle] = "A Title"
        it[mediaUri] = File("/dir/Music/File.mpg").toUri().toString()
        it[artistId] = 100
        it[albumId] = 100
      }

      ArtistAlbumTable.insert {
        it[artistId] = 5000
        it[albumId] = 5000
      }

      val artist = "Led Zeppelin"
      ArtistTable.insert { it[artistName] = artist }
      AlbumTable.insert {
        it[albumName] = "Killer Queen"
        it[artistName] = artist
      }
      setSuccessful()
    }
  }

  public suspend fun testForeignKeyIntegrity(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, MEDIA_TABLES, testDispatcher, enableForeignKeyConstraints = false) {
      createTablesWithFKViolations()

      query {
        ArtistAlbumTable.foreignKeyCheck().let { violations ->
          expect(violations).toHaveSize(2)
          expect(violations[0]).toBe(ForeignKeyViolation("ArtistAlbum", 1, "Album", 0))
          expect(violations[1]).toBe(ForeignKeyViolation("ArtistAlbum", 1, "Artist", 1))
        }
        MediaFileTable.foreignKeyCheck().let { violations ->
          expect(violations).toHaveSize(2)
          expect(violations[0]).toBe(ForeignKeyViolation("MediaFile", 1, "Album", 0))
          expect(violations[1]).toBe(ForeignKeyViolation("MediaFile", 1, "Artist", 1))
        }
      }
    }
  }
}
