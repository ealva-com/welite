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

package com.ealva.welite.test.db.trigger

import android.content.Context
import android.net.Uri
import com.ealva.welite.db.Transaction
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.case
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.longLiteral
import com.ealva.welite.db.expr.notLike
import com.ealva.welite.db.expr.raiseAbort
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.db.table.asExpression
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectAll
import com.ealva.welite.db.table.selectCount
import com.ealva.welite.db.table.where
import com.ealva.welite.db.trigger.Trigger
import com.ealva.welite.db.trigger.trigger
import com.ealva.welite.test.shared.AlbumTable
import com.ealva.welite.test.shared.ArtistAlbumTable
import com.ealva.welite.test.shared.ArtistTable
import com.ealva.welite.test.shared.MediaFileTable
import com.ealva.welite.test.shared.SqlExecutorSpy
import com.ealva.welite.test.shared.withTestDatabase
import com.nhaarman.expect.expect
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File

typealias IdTriple = Triple<Long, Long, Long>

val IdTriple.artistId: Long
  get() = first

val IdTriple.albumId: Long
  get() = second

val IdTriple.mediaId: Long
  get() = third

object CommonTriggerTests {
  fun testCreateTrigger() {
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

    SqlExecutorSpy().let { spy ->
      InsertMediaTrigger.create(spy)
      val execSqlList = spy.execSqlList
      expect(execSqlList).toHaveSize(1)
      expect(execSqlList[0]).toBe(
        """CREATE TRIGGER IF NOT EXISTS "InsertMediaTrigger" BEFORE INSERT ON "MediaFile" """ +
          """BEGIN SELECT CASE WHEN NEW.MediaUri NOT LIKE 'file:%' THEN RAISE(ABORT, """ +
          """'Abort, not URI') END; END;"""
      )
    }

    SqlExecutorSpy().let { spy ->
      DeleteMediaTrigger.create(spy)
      val execSqlList = spy.execSqlList
      expect(execSqlList).toHaveSize(1)
      expect(execSqlList[0]).toBe(
        """CREATE TRIGGER IF NOT EXISTS "DeleteMediaTrigger" AFTER DELETE ON "MediaFile" BEGIN """ +
          """DELETE FROM "Album" WHERE (SELECT COUNT(*) FROM "MediaFile" WHERE """ +
          """"MediaFile"."AlbumId" = OLD.AlbumId) = 0; DELETE FROM "Artist" WHERE (SELECT """ +
          """COUNT(*) FROM "MediaFile" WHERE "MediaFile"."ArtistId" = OLD.ArtistId) = 0; END;"""
      )
    }
  }

  suspend fun testExecDeleteArtistTrigger(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(
      context = appCtx,
      tables = listOf(MediaFileTable, ArtistTable, AlbumTable, ArtistAlbumTable),
      testDispatcher = testDispatcher,
      enableForeignKeyConstraints = false
    ) {
      transaction {
        DeleteArtistTrigger.create()
        val uri = Uri.fromFile(File("""/Music/Song.mp3"""))
        insertData("Led Zeppelin", "Houses of the Holy", "Dy'er Mak'er", uri)
      }

      query {
        expect(DeleteArtistTrigger.exists).toBe(true)
        expect(ArtistAlbumTable.selectAll().count()).toBe(1)
      }

      transaction {
        expect(ArtistTable.delete { ArtistTable.artistName eq "Led Zeppelin" }).toBe(1)
      }

      query {
        expect(ArtistTable.selectAll().count()).toBe(0)
        expect(ArtistAlbumTable.selectAll().count()).toBe(0)
      }
    }
  }

  suspend fun testExecDeleteAlbumTrigger(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withTestDatabase(
      context = appCtx,
      tables = listOf(MediaFileTable, ArtistTable, AlbumTable, ArtistAlbumTable),
      testDispatcher = testDispatcher,
      enableForeignKeyConstraints = false
    ) {
      transaction {
        DeleteAlbumTrigger.create()
        val uri = Uri.fromFile(File("""/Music/Song.mp3"""))
        insertData("Led Zeppelin", "Houses of the Holy", "Dy'er Mak'er", uri)
      }

      query {
        expect(ArtistAlbumTable.selectAll().count()).toBe(1)
        expect(DeleteAlbumTrigger.exists).toBe(true)
      }

      transaction {
        expect(DeleteAlbumTrigger.exists).toBe(true)
        expect(AlbumTable.delete { AlbumTable.albumName eq "Houses of the Holy" }).toBe(1)
      }

      query {
        expect(ArtistAlbumTable.selectAll().count()).toBe(0)
      }
    }
  }

  suspend fun testInsertTriggerValidUri(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withTestDatabase(
      context = appCtx,
      tables = listOf(MediaFileTable, ArtistTable, AlbumTable, ArtistAlbumTable),
      testDispatcher = testDispatcher,
      enableForeignKeyConstraints = false
    ) {
      transaction {
        InsertMediaTrigger.create()
        val uri = Uri.fromFile(File("""/Music/Song.mp3"""))
        insertData("Led Zeppelin", "Houses of the Holy", "Dy'er Mak'er", uri)
      }

      query {
        expect(ArtistAlbumTable.selectAll().count()).toBe(1)
        expect(InsertMediaTrigger.exists).toBe(true)
      }
    }
  }

  suspend fun testInsertTriggerInvalidUriCausesAbor(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withTestDatabase(
      context = appCtx,
      tables = listOf(MediaFileTable, ArtistTable, AlbumTable, ArtistAlbumTable),
      testDispatcher = testDispatcher,
      enableForeignKeyConstraints = false
    ) {
      transaction {
        InsertMediaTrigger.create()
        val uri = Uri.fromFile(File("""/Music/Song.mp3"""))
        insertData("Led Zeppelin", "Houses of the Holy", "Dy'er Mak'er", uri)
      }

      query {
        expect(InsertMediaTrigger.exists).toBe(true)
        expect(ArtistAlbumTable.selectAll().count()).toBe(1)
      }

      transaction {
        expect(InsertMediaTrigger.exists).toBe(true)
        insertData("An Artist", "An Album", "Song Title", Uri.EMPTY) // Uri.EMPTY should abort
      }
    }
  }

  suspend fun testDeleteMediaTrigger(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(
      context = appCtx,
      tables = listOf(MediaFileTable, ArtistTable, AlbumTable, ArtistAlbumTable),
      testDispatcher = testDispatcher,
      enableForeignKeyConstraints = false
    ) {
      var ids: IdTriple? = null
      transaction {
        DeleteMediaTrigger.create()
        val uri = Uri.fromFile(File("""/Music/Song.mp3"""))
        ids = insertData("Led Zeppelin", "Houses of the Holy", "Dy'er Mak'er", uri)
      }

      query {
        expect(ArtistTable.selectAll().count()).toBe(1)
        expect(AlbumTable.selectAll().count()).toBe(1)
        expect(DeleteMediaTrigger.exists).toBe(true)
      }

      transaction {
        val mediaId = checkNotNull(ids).mediaId
        MediaFileTable.delete { MediaFileTable.id eq mediaId }
      }

      query {
        expect(ArtistTable.selectAll().count()).toBe(0)
        expect(AlbumTable.selectAll().count()).toBe(0)
      }
    }
  }

  suspend fun testDeleteMediaAndTriggersCascade(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withTestDatabase(
      context = appCtx,
      tables = listOf(MediaFileTable, ArtistTable, AlbumTable, ArtistAlbumTable),
      testDispatcher = testDispatcher,
      enableForeignKeyConstraints = false
    ) {
      var ids: IdTriple? = null
      transaction {
        DeleteAlbumTrigger.create()
        DeleteArtistTrigger.create()
        DeleteMediaTrigger.create()
        val uri = Uri.fromFile(File("""/Music/Song.mp3"""))
        ids = insertData("Led Zeppelin", "Houses of the Holy", "Dy'er Mak'er", uri)
      }

      query {
        expect(ArtistTable.selectAll().count()).toBe(1)
        expect(AlbumTable.selectAll().count()).toBe(1)
        expect(ArtistAlbumTable.selectAll().count()).toBe(1)
        expect(DeleteMediaTrigger.exists).toBe(true)
        expect(DeleteAlbumTrigger.exists).toBe(true)
        expect(DeleteArtistTrigger.exists).toBe(true)
      }

      transaction {
        val mediaId = checkNotNull(ids).mediaId
        MediaFileTable.delete { MediaFileTable.id eq mediaId }
      }

      query {
        expect(ArtistTable.selectAll().count()).toBe(0)
        expect(AlbumTable.selectAll().count()).toBe(0)
        expect(AlbumTable.selectAll().count()).toBe(0)
      }
    }
  }

  private fun Transaction.insertData(
    artist: String,
    album: String,
    title: String,
    uri: Uri
  ): Triple<Long, Long, Long> {
    val idArtist: Long = ArtistTable.select(ArtistTable.id)
      .where { ArtistTable.artistName eq artist }
      .sequence { it[ArtistTable.id] }
      .firstOrNull() ?: ArtistTable.insert { it[artistName] = artist }

    val idAlbum: Long = AlbumTable.select(AlbumTable.id)
      .where { AlbumTable.albumName eq album }
      .sequence { it[AlbumTable.id] }
      .firstOrNull() ?: AlbumTable.insert {
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

val InsertMediaTrigger = MediaFileTable.trigger(
  name = "InsertMediaTrigger",
  beforeAfter = Trigger.BeforeAfter.BEFORE,
  event = Trigger.Event.INSERT
) {
  select(
    case().caseWhen(
      new(MediaFileTable.mediaUri) notLike "file:%",  // must start with "file:"
      raiseAbort("Abort, not URI")
    )
  ).where(null)
}

val DeleteMediaTrigger = MediaFileTable.trigger(
  name = "DeleteMediaTrigger",
  beforeAfter = Trigger.BeforeAfter.AFTER,
  event = Trigger.Event.DELETE
) {
  val mediaAlbumCount: Expression<Long> =
    (MediaFileTable.selectCount { MediaFileTable.albumId eq old(MediaFileTable.albumId) })
      .asExpression()

  AlbumTable.delete { mediaAlbumCount eq longLiteral(0) }

  val mediaArtistCount: Expression<Long> =
    (MediaFileTable.selectCount { MediaFileTable.artistId eq old(MediaFileTable.artistId) })
      .asExpression()

  ArtistTable.delete { mediaArtistCount eq longLiteral(0) }
}
