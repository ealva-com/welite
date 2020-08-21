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

package com.ealva.welite.db

import android.content.Context
import android.os.Build
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.db.table.ForeignKeyAction
import com.ealva.welite.test.common.AlbumTable
import com.ealva.welite.test.common.ArtistAlbumTable
import com.ealva.welite.test.common.ArtistTable
import com.ealva.welite.test.common.CoroutineRule
import com.ealva.welite.test.common.MediaFileTable
import com.ealva.welite.test.common.runBlockingTest
import com.ealva.welite.test.common.withTestDatabase
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class ForeignKeySchemaTests {
  @get:Rule var coroutineRule = CoroutineRule()
  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun testForeignKeyList() = coroutineRule.runBlockingTest {
    withTestDatabase(
      context = appCtx,
      tables = listOf(ArtistAlbumTable, MediaFileTable, ArtistTable, AlbumTable),
      testDispatcher = coroutineRule.testDispatcher,
      enableForeignKeyConstraints = false
    ) {
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
              onDelete = ForeignKeyAction.CASCADE
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
              onDelete = ForeignKeyAction.CASCADE
            )
          )
        }
      }
    }
  }

  @Test
  fun testForeignKeyIntegrity() = coroutineRule.runBlockingTest {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      withTestDatabase(
        context = appCtx,
        tables = listOf(ArtistAlbumTable, MediaFileTable, ArtistTable, AlbumTable),
        testDispatcher = coroutineRule.testDispatcher,
        enableForeignKeyConstraints = false
      ) {
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

  private suspend fun Database.createTablesWithFKViolations() {
    transaction {
      expect(MediaFileTable.exists).toBe(true)
      expect(AlbumTable.exists).toBe(true)
      expect(ArtistTable.exists).toBe(true)
      expect(ArtistAlbumTable.exists).toBe(true)

      MediaFileTable.insert {
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
}
