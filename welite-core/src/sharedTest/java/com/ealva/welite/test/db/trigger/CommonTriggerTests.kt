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
import com.ealva.welite.db.expr.literal
import com.ealva.welite.db.expr.neq
import com.ealva.welite.db.expr.notLike
import com.ealva.welite.db.expr.raiseAbort
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.db.table.asExpression
import com.ealva.welite.db.trigger.Trigger
import com.ealva.welite.db.trigger.deleteTrigger
import com.ealva.welite.db.trigger.insertTrigger
import com.ealva.welite.db.trigger.updateTrigger
import com.ealva.welite.db.type.asIdentity
import com.ealva.welite.test.shared.AlbumTable
import com.ealva.welite.test.shared.ArtistAlbumTable
import com.ealva.welite.test.shared.ArtistTable
import com.ealva.welite.test.shared.MEDIA_TABLES
import com.ealva.welite.test.shared.MediaFileTable
import com.ealva.welite.test.shared.SqlExecutorSpy
import com.ealva.welite.test.shared.withTestDatabase
import com.nhaarman.expect.expect
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File

public typealias IdTriple = Triple<Long, Long, Long>

public val IdTriple.artistId: Long
  get() = first

public val IdTriple.albumId: Long
  get() = second

public val IdTriple.mediaId: Long
  get() = third

public object CommonTriggerTests {
  public fun testCreateTrigger() {
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

  public fun testDropTrigger() {
    SqlExecutorSpy().let { spy ->
      DeleteArtistTrigger.drop(spy)
      val execSqlList = spy.execSqlList
      expect(execSqlList).toHaveSize(1)
      expect(execSqlList[0]).toBe("DROP TRIGGER IF EXISTS \"DeleteArtistTrigger\"")
    }
  }

  public suspend fun testExecDeleteArtistTrigger(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withTestDatabase(
      context = appCtx,
      tables = MEDIA_TABLES,
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

  public suspend fun testExecDeleteAlbumTrigger(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withTestDatabase(appCtx, MEDIA_TABLES, testDispatcher, enableForeignKeyConstraints = false) {
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

  public suspend fun testInsertTriggerValidUri(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withTestDatabase(appCtx, MEDIA_TABLES, testDispatcher) {
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

  public suspend fun testInsertTriggerInvalidUriCausesAbort(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withTestDatabase(appCtx, MEDIA_TABLES, testDispatcher) {
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

  public suspend fun testDeleteMediaTrigger(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, MEDIA_TABLES, testDispatcher, enableForeignKeyConstraints = false) {
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

  public suspend fun testDeleteMediaAndTriggersCascade(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withTestDatabase(appCtx, MEDIA_TABLES, testDispatcher) {
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

  public fun testNewColumnForDeleteEventIsInvalid() {
    MediaFileTable.deleteTrigger(
      name = "BadTrigger",
      beforeAfter = Trigger.BeforeAfter.BEFORE,
    ) {
      select(
        case().caseWhen(
          new(MediaFileTable.mediaUri) notLike "file:%", // will throw
          raiseAbort("Abort, not URI")
        )
      )
    }
  }

  public fun testNewRejectsColumnWithWrongTable() {
    MediaFileTable.updateTrigger(
      name = "BadTrigger",
      beforeAfter = Trigger.BeforeAfter.BEFORE,
    ) {
      new(AlbumTable.id)
    }
  }

  public fun testOldRejectsColumnWithWrongTable() {
    MediaFileTable.updateTrigger(
      name = "BadTrigger",
      beforeAfter = Trigger.BeforeAfter.BEFORE,
    ) {
      old(AlbumTable.id)
    }
  }

  public fun testOldColumnForInsertEventIsInvalid() {
    MediaFileTable.insertTrigger(
      name = "BadTrigger",
      beforeAfter = Trigger.BeforeAfter.BEFORE,
    ) {
      select(
        case().caseWhen(
          old(MediaFileTable.mediaUri) notLike "file:%", // will throw
          raiseAbort("Abort, not URI")
        )
      )
    }
  }

  public fun testInsertOnInsertEvent() {
    val trigger = MediaFileTable.insertTrigger(
      name = "InsertMediaTrigger",
      beforeAfter = Trigger.BeforeAfter.BEFORE,
    ) {
      AlbumTable.insert {
        it[albumName] = "Some Album"
      }
    }
    SqlExecutorSpy().let { spy ->
      trigger.create(spy)
      val execSqlList = spy.execSqlList
      expect(execSqlList).toHaveSize(1)
      expect(execSqlList[0]).toBe(
        """CREATE TRIGGER IF NOT EXISTS "InsertMediaTrigger" BEFORE INSERT ON "MediaFile" BEGIN""" +
          """ INSERT INTO "Album" ("AlbumName") VALUES ('Some Album'); END;"""
      )
    }
  }

  public fun testUpdateOnInsertTrigger() {
    val trigger = MediaFileTable.insertTrigger(
      name = "InsertMediaTrigger",
      beforeAfter = Trigger.BeforeAfter.BEFORE,
    ) {
      AlbumTable.update {
        it[albumName] = "Some Album"
      }.where { new(MediaFileTable.mediaUri) notLike "file:%" }
    }
    SqlExecutorSpy().let { spy ->
      trigger.create(spy)
      val execSqlList = spy.execSqlList
      expect(execSqlList).toHaveSize(1)
      expect(execSqlList[0]).toBe(
        """CREATE TRIGGER IF NOT EXISTS "InsertMediaTrigger" BEFORE INSERT ON "MediaFile" BEGIN""" +
          """ UPDATE "Album" SET "AlbumName"='Some Album' WHERE NEW.MediaUri NOT LIKE 'file:%';""" +
          """ END;"""
      )
    }
  }

  public fun testOldAndNewColumnIdentity() {
    MediaFileTable.updateTrigger(
      name = "BadTrigger",
      beforeAfter = Trigger.BeforeAfter.AFTER,
    ) {
      // statement is required, doesn't matter what for this test
      select(
        case().caseWhen(
          new(MediaFileTable.mediaUri) notLike "file:%", // must start with "file:"
          raiseAbort("Abort, not URI")
        )
      )

      val oldCol = old(MediaFileTable.mediaUri)
      val newCol = new(MediaFileTable.albumId)
      expect(oldCol.identity()).toBe("OLD.MediaUri".asIdentity(forceQuote = true))
      expect(newCol.identity()).toBe("NEW.AlbumId".asIdentity(forceQuote = true))
    }
  }

  public fun testTempTrigger() {
    val trigger = MediaFileTable.insertTrigger(
      name = "InsertMediaTrigger",
      beforeAfter = Trigger.BeforeAfter.BEFORE,
      temporary = true,
    ) {
      AlbumTable.insert {
        it[albumName] = "Some Album"
      }
    }
    SqlExecutorSpy().let { spy ->
      trigger.create(spy)
      val execSqlList = spy.execSqlList
      expect(execSqlList).toHaveSize(1)
      expect(execSqlList[0]).toBe(
        """CREATE TEMP TRIGGER IF NOT EXISTS "InsertMediaTrigger" BEFORE INSERT ON "MediaFile"""" +
          """ BEGIN INSERT INTO "Album" ("AlbumName") VALUES ('Some Album'); END;"""
      )
    }
  }

  public fun testUpdateTriggerWhenCondition() {
    val trigger = MediaFileTable.updateTrigger(
      name = "InsertMediaTrigger",
      beforeAfter = Trigger.BeforeAfter.BEFORE,
      temporary = true,
      triggerCondition = { table ->
        old(table.id) neq new(table.id)
      }
    ) {
      AlbumTable.insert {
        it[albumName] = "Some Album"
      }
    }
    SqlExecutorSpy().let { spy ->
      trigger.create(spy)
      val execSqlList = spy.execSqlList
      expect(execSqlList).toHaveSize(1)
      expect(execSqlList[0]).toBe(
        """CREATE TEMP TRIGGER IF NOT EXISTS "InsertMediaTrigger" BEFORE UPDATE ON""" +
          """ "MediaFile" WHEN OLD._id <> NEW._id BEGIN INSERT INTO "Album" ("AlbumName")""" +
          """ VALUES ('Some Album'); END;"""
      )
    }
  }

  public fun testInsertTriggerWhenCondition() {
    val trigger = MediaFileTable.insertTrigger(
      name = "InsertMediaTrigger",
      beforeAfter = Trigger.BeforeAfter.BEFORE,
      triggerCondition = { table ->
        new(table.id) neq 1
      }
    ) {
      select(
        case().caseWhen(
          new(MediaFileTable.mediaUri) notLike "file:%", // must start with "file:"
          raiseAbort("Abort, not URI")
        )
      )
    }
    SqlExecutorSpy().let { spy ->
      trigger.create(spy)
      val execSqlList = spy.execSqlList
      expect(execSqlList).toHaveSize(1)
      expect(execSqlList[0]).toBe(
        """CREATE TRIGGER IF NOT EXISTS "InsertMediaTrigger" BEFORE""" +
          """ INSERT ON "MediaFile" WHEN NEW._id <> 1 BEGIN SELECT CASE WHEN NEW.MediaUri NOT""" +
          """ LIKE 'file:%' THEN RAISE(ABORT, 'Abort, not URI') END; END;"""
      )
    }
  }

  public fun testDeleteTriggerWhenCondition() {
    val trigger = AlbumTable.deleteTrigger(
      name = "DeleteAlbumTrigger",
      beforeAfter = Trigger.BeforeAfter.BEFORE,
      triggerCondition = { table ->
        old(table.id) neq 1
      }
    ) {
      ArtistAlbumTable.delete { ArtistAlbumTable.albumId eq old(AlbumTable.id) }
    }
    SqlExecutorSpy().let { spy ->
      trigger.create(spy)
      val execSqlList = spy.execSqlList
      expect(execSqlList).toHaveSize(1)
      expect(execSqlList[0]).toBe(
        """CREATE TRIGGER IF NOT EXISTS "DeleteAlbumTrigger" BEFORE DELETE ON "Album" WHEN""" +
          """ OLD._id <> 1 BEGIN DELETE FROM "ArtistAlbum" WHERE "ArtistAlbum"."AlbumId" =""" +
          """ OLD._id; END;"""
      )
    }
  }

  public fun testTriggerConditionNewRejectsColumnWithWrongTable() {
    MediaFileTable.updateTrigger(
      name = "InsertMediaTrigger",
      beforeAfter = Trigger.BeforeAfter.BEFORE,
      triggerCondition = { table ->
        old(table.id) neq new(ArtistTable.id)
      }
    ) {
      AlbumTable.insert {
        it[albumName] = "Some Album"
      }
    }
  }

  public fun testTriggerConditionOldRejectsColumnWithWrongTable() {
    MediaFileTable.updateTrigger(
      name = "InsertMediaTrigger",
      beforeAfter = Trigger.BeforeAfter.BEFORE,
      triggerCondition = { table ->
        old(ArtistTable.id) neq new(table.id)
      }
    ) {
      AlbumTable.insert {
        it[albumName] = "Some Album"
      }
    }
  }

  public fun testWhenConditionNewColumnForDeleteEventIsInvalid() {
    MediaFileTable.deleteTrigger(
      name = "InsertMediaTrigger",
      beforeAfter = Trigger.BeforeAfter.BEFORE,
      triggerCondition = { table ->
        new(table.id) neq 1
      }
    ) {
      AlbumTable.insert {
        it[albumName] = "Some Album"
      }
    }
  }

  public fun testWhenConditionOldColumnForInsertEventIsInvalid() {
    MediaFileTable.insertTrigger(
      name = "InsertMediaTrigger",
      beforeAfter = Trigger.BeforeAfter.BEFORE,
      triggerCondition = {
        old(ArtistTable.id) neq 1
      }
    ) {
      AlbumTable.insert {
        it[albumName] = "Some Album"
      }
    }
  }

  public fun testBindingArgInTriggerRejected() {
    val trigger = MediaFileTable.updateTrigger(
      name = "InsertMediaTrigger",
      beforeAfter = Trigger.BeforeAfter.BEFORE,
      temporary = true,
      triggerCondition = { table ->
        old(table.id) neq new(table.id)
      }
    ) {
      AlbumTable.insert {
        it[albumName].bindArg()
      }
    }
    trigger.create(SqlExecutorSpy())
  }

  public fun testUpdateColumnsTrigger() {
    val trigger = MediaFileTable.updateTrigger(
      name = "InsertMediaTrigger",
      beforeAfter = Trigger.BeforeAfter.BEFORE,
      updateColumns = listOf(MediaFileTable.mediaTitle, MediaFileTable.mediaUri)
    ) {
      AlbumTable.insert {
        it[albumName] = "Some Album"
      }
    }
    SqlExecutorSpy().let { spy ->
      trigger.create(spy)
      val execSqlList = spy.execSqlList
      expect(execSqlList).toHaveSize(1)
      expect(execSqlList[0]).toBe(
        """CREATE TRIGGER IF NOT EXISTS "InsertMediaTrigger" BEFORE UPDATE OF "MediaTitle",""" +
          """ "MediaUri" ON "MediaFile" BEGIN INSERT INTO "Album" ("AlbumName") VALUES""" +
          """ ('Some Album'); END;"""
      )
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
      .singleOrNull() ?: ArtistTable.insert { it[artistName] = artist }

    val idAlbum: Long = AlbumTable.select(AlbumTable.id)
      .where { AlbumTable.albumName eq album }
      .sequence { it[AlbumTable.id] }
      .singleOrNull() ?: AlbumTable.insert {
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

public val DeleteArtistTrigger: Trigger<ArtistTable> = ArtistTable.deleteTrigger(
  name = "DeleteArtistTrigger",
  beforeAfter = Trigger.BeforeAfter.BEFORE,
) {
  ArtistAlbumTable.delete { ArtistAlbumTable.artistId eq old(ArtistTable.id) }
}

public val DeleteAlbumTrigger: Trigger<AlbumTable> = AlbumTable.deleteTrigger(
  name = "DeleteAlbumTrigger",
  beforeAfter = Trigger.BeforeAfter.BEFORE,
) {
  ArtistAlbumTable.delete { ArtistAlbumTable.albumId eq old(AlbumTable.id) }
}

public val InsertMediaTrigger: Trigger<MediaFileTable> = MediaFileTable.insertTrigger(
  name = "InsertMediaTrigger",
  beforeAfter = Trigger.BeforeAfter.BEFORE,
) {
  select(
    case().caseWhen(
      new(MediaFileTable.mediaUri) notLike "file:%", // must start with "file:"
      raiseAbort("Abort, not URI")
    )
  )
}

public val DeleteMediaTrigger: Trigger<MediaFileTable> = MediaFileTable.deleteTrigger(
  name = "DeleteMediaTrigger",
  beforeAfter = Trigger.BeforeAfter.AFTER,
) {
  val mediaAlbumCount: Expression<Long> =
    (MediaFileTable.selectCount { MediaFileTable.albumId eq old(MediaFileTable.albumId) })
      .asExpression()

  AlbumTable.delete { mediaAlbumCount eq literal(0) }

  val mediaArtistCount: Expression<Long> =
    (MediaFileTable.selectCount { MediaFileTable.artistId eq old(MediaFileTable.artistId) })
      .asExpression()

  ArtistTable.delete { mediaArtistCount eq literal(0) }
}
