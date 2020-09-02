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

package com.ealva.welite.db.view

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.db.Transaction
import com.ealva.welite.db.expr.SortOrder
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.greaterEq
import com.ealva.welite.db.table.JoinType
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.db.table.all
import com.ealva.welite.db.table.select
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
  fun `test create view`() {
    val view = object : View(
      "FullMedia",
      MediaFileTable.select(MediaFileTable.id).where(MediaFileTable.id eq 1)
    ) {
      @Suppress("unused") val mediaId = column("mediaFileId", MediaFileTable.id)
    }

    SqlExecutorSpy().let { spy ->
      view.create(spy)
      val ddl = spy.execSqlList
      expect(ddl).toHaveSize(1)
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        expect(ddl[0]).toBe(
          """CREATE VIEW IF NOT EXISTS "FullMedia" AS SELECT "MediaFile"."_id" FROM "MediaFile"""" +
            """ WHERE "MediaFile"."_id" = 1"""
        )
      } else {
        expect(ddl[0]).toBe(
          """CREATE VIEW IF NOT EXISTS "FullMedia" ("mediaFileId") AS SELECT """ +
            """"MediaFile"."_id" FROM "MediaFile" WHERE "MediaFile"."_id" = 1"""
        )
      }
    }
    SqlExecutorSpy().let { spy ->
      view.drop(spy)
      expect(spy.execSqlList).toHaveSize(1)
      expect(spy.execSqlList[0]).toBe("""DROP VIEW IF EXISTS "FullMedia"""")
    }
  }

  @Test
  fun `test view from existing query`() {
    SqlExecutorSpy().let { spy ->
      FullMediaView.create(spy)
      val ddl = spy.execSqlList
      expect(ddl).toHaveSize(1)
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        expect(ddl[0]).toBe(
          """CREATE VIEW IF NOT EXISTS "FullMedia" AS SELECT "MediaFile"."_id",""" +
            """ "MediaFile"."MediaTitle", "Album"."AlbumName", "Artist"."ArtistName" FROM""" +
            """ "MediaFile" LEFT JOIN "Album" ON "MediaFile"."AlbumId" = "Album"."_id" LEFT""" +
            """ JOIN "Artist" ON "MediaFile"."ArtistId" = "Artist"."_id""""
        )
      } else {
        expect(ddl[0]).toBe(
          """CREATE VIEW IF NOT EXISTS "FullMedia" ("FullMedia_MediaId", "FullMedia_MediaTitle"""" +
            """, "FullMedia_AlbumName", "FullMedia_ArtistName") AS SELECT "MediaFile"."_id",""" +
            """ "MediaFile"."MediaTitle", "Album"."AlbumName", "Artist"."ArtistName" FROM""" +
            """ "MediaFile" LEFT JOIN "Album" ON "MediaFile"."AlbumId" = "Album"."_id" LEFT""" +
            """ JOIN "Artist" ON "MediaFile"."ArtistId" = "Artist"."_id""""
        )
      }
    }
  }

  @Test
  fun `test query view`() = coroutineRule.runBlockingTest {
    withTestDatabase(
      context = appCtx,
      tables = listOf(MediaFileTable, ArtistTable, AlbumTable, ArtistAlbumTable),
      testDispatcher = coroutineRule.testDispatcher,
      enableForeignKeyConstraints = true
    ) {
      val tomorrow = Triple(
        "Tomorrow Never Knows",
        "The Beatles",
        "Revolver",
      )
      val kashmir = Triple(
        "Kashmir",
        "Led Zeppelin",
        "Physical Graffiti"
      )
      val dyer = Triple(
        "Dy'er Mak'er",
        "Led Zeppelin",
        "Houses of the Holy"
      )
      transaction {
        FullMediaView.create()

        insertData(
          tomorrow.first,
          tomorrow.second,
          tomorrow.third,
          "file:\\tomorrowneverknows"
        )
        insertData(
          kashmir.first,
          kashmir.second,
          kashmir.third,
          "file:\\kashmir.mp3"
        )
        insertData(
          dyer.first,
          dyer.second,
          dyer.third,
          "file:\\dyermaker.mp3"
        )
        setSuccessful()
      }
      query {
        val result = FullMediaView
          .select()
          .where { FullMediaView.mediaId greaterEq 1 }
          .orderBy(
            FullMediaView.artistName to SortOrder.DESC,
            FullMediaView.albumName to SortOrder.ASC
          )
          .sequence { cursor ->
            Triple(
              cursor[FullMediaView.mediaTitle],
              cursor[FullMediaView.artistName],
              cursor[FullMediaView.albumName]
            )
          }.toList()
        expect(result).toHaveSize(3)
        expect(result[0]).toBe(tomorrow)
        expect(result[1]).toBe(dyer)
        expect(result[2]).toBe(kashmir)
      }
    }
  }

  private fun Transaction.insertData(
    title: String,
    artist: String,
    album: String,
    uri: String
  ): Triple<Long, Long, Long> {
    val idArtist: Long = ArtistTable.select(ArtistTable.id)
      .where { ArtistTable.artistName eq artist }
      .sequence { cursor -> cursor[ArtistTable.id] }
      .singleOrNull() ?: ArtistTable.insert { it[artistName] = artist }

    val idAlbum: Long = AlbumTable.select(AlbumTable.id)
      .where {
        AlbumTable.albumName eq AlbumTable.albumName.bindArg() and (AlbumTable.artistName eq artist)
      }
      .sequence({ it[0] = album }) { it[AlbumTable.id] }
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
      it[mediaUri] = uri
      it[artistId] = idArtist
      it[albumId] = idAlbum
    }
    return Triple(idArtist, idAlbum, mediaId)
  }
}

@ExperimentalUnsignedTypes
private val ViewTestsQuery = MediaFileTable
  .join(AlbumTable, JoinType.LEFT, MediaFileTable.albumId, AlbumTable.id)
  .join(ArtistTable, JoinType.LEFT, MediaFileTable.artistId, ArtistTable.id)
  .select(
    MediaFileTable.id,
    MediaFileTable.mediaTitle,
    AlbumTable.albumName,
    ArtistTable.artistName
  )
  .all()
  .build()

@ExperimentalUnsignedTypes
object FullMediaView : View(
  "FullMedia",
  ViewTestsQuery
) {
  val mediaId = column("FullMedia_MediaId", MediaFileTable.id)
  val mediaTitle = column("FullMedia_MediaTitle", MediaFileTable.mediaTitle)
  val albumName = column("FullMedia_AlbumName", AlbumTable.albumName)
  val artistName = column("FullMedia_ArtistName", ArtistTable.artistName)
}
