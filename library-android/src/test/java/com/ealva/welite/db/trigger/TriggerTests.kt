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

package com.ealva.welite.db.trigger

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.db.Transaction
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectAll
import com.ealva.welite.db.table.where
import com.ealva.welite.test.common.AlbumTable
import com.ealva.welite.test.common.ArtistAlbumTable
import com.ealva.welite.test.common.ArtistTable
import com.ealva.welite.test.common.CoroutineRule
import com.ealva.welite.test.common.MediaFileTable
import com.ealva.welite.test.common.SqlExecutorSpy
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

typealias Ids = Triple<Long, Long, Long>

val Ids.artistId: Long
  get() = first

val Ids.albumId: Long
  get() = second

val Ids.mediaId: Long
  get() = third

@ExperimentalUnsignedTypes
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class ViewTests {
  @get:Rule var coroutineRule = CoroutineRule()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test create trigger`() = coroutineRule.runBlockingTest {
    SqlExecutorSpy().let { spy ->
      DeleteArtistTrigger.create(spy)
      val execSqlList = spy.execSqlList
      expect(execSqlList).toHaveSize(1)
      expect(execSqlList[0]).toBe(
        """CREATE TRIGGER IF NOT EXISTS "DeleteArtistTrigger" BEFORE DELETE ON "Artist" BEGIN""" +
          """ DELETE FROM "ArtistAlbum" WHERE "ArtistAlbum"."ArtistId" = OLD._id; END;"""
      )
    }

    SqlExecutorSpy().let { spy ->
      DeleteAlbumTrigger.create(spy)
      val execSqlList = spy.execSqlList
      expect(execSqlList).toHaveSize(1)
      expect(execSqlList[0]).toBe(
        """CREATE TRIGGER IF NOT EXISTS "DeleteAlbumTrigger" BEFORE DELETE ON "Album" BEGIN""" +
          """ DELETE FROM "ArtistAlbum" WHERE "ArtistAlbum"."AlbumId" = OLD._id; END;"""
      )
    }

//    SqlExecutorSpy().let { spy ->
//      InsertMediaTrigger.create(spy)
//      val execSqlList = spy.execSqlList
//      expect(execSqlList).toHaveSize(1)
//      expect(execSqlList[0]).toBe("")
//    }
  }

  @Test
  fun `test exec delete artist trigger`() = coroutineRule.runBlockingTest {
    withTestDatabase(
      context = appCtx,
      tables = listOf(MediaFileTable, ArtistTable, AlbumTable, ArtistAlbumTable),
      testDispatcher = coroutineRule.testDispatcher,
      enableForeignKeyConstraints = false
    ) {
      transaction {
        DeleteArtistTrigger.create()
        val uri = Uri.fromFile(File("""/Music/Song.mp3"""))
        insertData("Led Zeppelin", "Houses of the Holy", "Dy'er Mak'er", uri)
        setSuccessful()
      }

      query {
        expect(DeleteArtistTrigger.exists).toBe(true)
        expect(ArtistAlbumTable.selectAll().count()).toBe(1)
      }

      transaction {
        expect(ArtistTable.delete { ArtistTable.artistName eq "Led Zeppelin" }).toBe(1)
        setSuccessful()
      }

      query {
        expect(ArtistTable.selectAll().count()).toBe(0)
        expect(ArtistAlbumTable.selectAll().count()).toBe(0)
      }
    }
  }

  @Test
  fun `test exec delete album trigger`() = coroutineRule.runBlockingTest {
    withTestDatabase(
      context = appCtx,
      tables = listOf(MediaFileTable, ArtistTable, AlbumTable, ArtistAlbumTable),
      testDispatcher = coroutineRule.testDispatcher,
      enableForeignKeyConstraints = false
    ) {
      transaction {
        DeleteAlbumTrigger.create()
        val uri = Uri.fromFile(File("""/Music/Song.mp3"""))
        insertData("Led Zeppelin", "Houses of the Holy", "Dy'er Mak'er", uri)
        setSuccessful()
      }

      query {
        expect(ArtistAlbumTable.selectAll().count()).toBe(1)
        expect(DeleteAlbumTrigger.exists).toBe(true)
      }

      transaction {
        expect(DeleteAlbumTrigger.exists).toBe(true)
        expect(AlbumTable.delete { AlbumTable.albumName eq "Houses of the Holy" }).toBe(1)
        setSuccessful()
      }

      query {
        expect(ArtistAlbumTable.selectAll().count()).toBe(0)
      }
    }
  }

  private fun Transaction.insertData(
    artist: String,
    album: String,
    title: String,
    uri: Uri
  ): Triple<Long, Long, Long> {
    var idArtist: Long = 0
    ArtistTable.select(ArtistTable.id)
      .where { ArtistTable.artistName eq artist }
      .forEach {
        idArtist = it[ArtistTable.id]
      }

    if (idArtist == 0L) idArtist = ArtistTable.insert { it[artistName] = artist }

    var idAlbum: Long = 0
    AlbumTable.select(AlbumTable.id)
      .where { AlbumTable.albumName eq album }
      .forEach {
        idAlbum = it[AlbumTable.id]
      }

    if (idAlbum == 0L) idAlbum = AlbumTable.insert {
      it[albumName] = album
      it[artistName] = artist
    }

    ArtistAlbumTable.insert(OnConflict.Ignore) {
      it[artistId] = idArtist
      it[albumId] = idAlbum
    }

    val mediaId = MediaFileTable.insert {
      it[mediaTitle] = title
      it[mediaUri] = uri.toString()
      it[artistId] = idArtist
      it[albumId] = idAlbum
    }
    return Triple(idArtist, idAlbum, mediaId)
  }
}

val DeleteArtistTrigger = ArtistTable.trigger(
  name = "DeleteArtistTrigger",
  beforeAfter = Trigger.BeforeAfter.BEFORE,
  event = Trigger.Event.DELETE
) {
  ArtistAlbumTable.delete { ArtistAlbumTable.artistId eq old(ArtistTable.id) }
}

val DeleteAlbumTrigger = AlbumTable.trigger(
  name = "DeleteAlbumTrigger",
  beforeAfter = Trigger.BeforeAfter.BEFORE,
  event = Trigger.Event.DELETE
) {
  ArtistAlbumTable.delete { ArtistAlbumTable.albumId eq old(AlbumTable.id) }
}

//val InsertMediaTrigger = MediaFileTable.trigger(
//  name = "InsertMediaTrigger",
//  beforeAfter = Trigger.BeforeAfter.BEFORE,
//  event = Trigger.Event.INSERT
//) {
//  MediaFileTable.select(
//    case()
//      .When(new(MediaFileTable.mediaUri) notLike "file:%", raiseAbort<String>("Abort, not URI"))
//  )
//}
