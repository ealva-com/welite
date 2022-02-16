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

package com.ealva.welite.test.db.view

import android.content.Context
import android.os.Build
import com.ealva.welite.db.Transaction
import com.ealva.welite.db.expr.Order
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.greaterEq
import com.ealva.welite.db.table.CollateNoCase
import com.ealva.welite.db.table.JoinType
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.db.table.all
import com.ealva.welite.db.table.and
import com.ealva.welite.db.table.ordersBy
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selects
import com.ealva.welite.db.table.by
import com.ealva.welite.db.table.toQuery
import com.ealva.welite.db.table.where
import com.ealva.welite.db.view.View
import com.ealva.welite.db.view.ViewColumn
import com.ealva.welite.test.shared.AlbumTable
import com.ealva.welite.test.shared.ArtistAlbumTable
import com.ealva.welite.test.shared.ArtistTable
import com.ealva.welite.test.shared.MEDIA_TABLES
import com.ealva.welite.test.shared.MediaFileTable
import com.ealva.welite.test.shared.SqlExecutorSpy
import com.ealva.welite.test.shared.withTestDatabase
import com.nhaarman.expect.expect
import kotlinx.coroutines.CoroutineDispatcher

@ExperimentalUnsignedTypes
public object CommonViewTests {
  public fun testCreateView() {
    val view = object : View(
      builder = MediaFileTable.select { id }.where { id eq 1 },
      name = "Full Media",
      forceQuoteName = true
    ) {
      @Suppress("unused")
      val mediaId = column(MediaFileTable.id, "mediaFileId")
    }

    SqlExecutorSpy().let { spy ->
      view.create(spy)
      val ddl = spy.execSqlList
      expect(ddl).toHaveSize(1)
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        expect(ddl[0]).toBe(
          """CREATE VIEW IF NOT EXISTS "Full Media" AS SELECT MediaFile._id FROM MediaFile""" +
            """ WHERE MediaFile._id = 1"""
        )
      } else {
        expect(ddl[0]).toBe(
          """CREATE VIEW IF NOT EXISTS "Full Media" (mediaFileId) AS SELECT """ +
            """MediaFile._id FROM MediaFile WHERE MediaFile._id = 1"""
        )
      }
    }
    SqlExecutorSpy().let { spy ->
      view.drop(spy)
      expect(spy.execSqlList).toHaveSize(1)
      expect(spy.execSqlList[0]).toBe("""DROP VIEW IF EXISTS "Full Media"""")
    }
  }

  @ExperimentalUnsignedTypes
  public fun testViewFromExistingQuery() {
    SqlExecutorSpy().let { spy ->
      FullMediaView.create(spy)
      val ddl = spy.execSqlList
      expect(ddl).toHaveSize(1)
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        expect(ddl[0]).toBe(
          """CREATE VIEW IF NOT EXISTS "Full Media" AS SELECT MediaFile._id,""" +
            """ MediaFile.MediaTitle, Album.AlbumName, Artist.ArtistName FROM""" +
            """ MediaFile LEFT JOIN Album ON MediaFile.AlbumId = Album._id LEFT""" +
            """ JOIN Artist ON MediaFile.ArtistId = Artist._id"""
        )
      } else {
        expect(ddl[0]).toBe(
          """CREATE VIEW IF NOT EXISTS "Full Media" (FullMedia_MediaId, FullMedia_MediaTitle""" +
            """, FullMedia_AlbumName, FullMedia_ArtistName) AS SELECT MediaFile._id,""" +
            """ MediaFile.MediaTitle, Album.AlbumName, Artist.ArtistName FROM""" +
            """ MediaFile LEFT JOIN Album ON MediaFile.AlbumId = Album._id LEFT""" +
            """ JOIN Artist ON MediaFile.ArtistId = Artist._id"""
        )
      }
    }
  }

  public suspend fun testQueryView(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(
      context = appCtx,
      tables = MEDIA_TABLES,
      otherCreatables = listOf(FullMediaView),
      testDispatcher = testDispatcher
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
          .selects()
          .where { mediaId greaterEq 1 }
          .ordersBy { listOf(artistName by Order.DESC and CollateNoCase, albumName by Order.ASC) }
          .sequence { cursor -> Triple(cursor[mediaTitle], cursor[artistName], cursor[albumName]) }
          .toList()
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
    val idArtist: Long = ArtistTable.select { id }
      .where { artistName eq artist }
      .sequence { cursor -> cursor[id] }
      .singleOrNull() ?: ArtistTable.insert { it[artistName] = artist }

    val idAlbum: Long = AlbumTable.select { id }
      .where { albumName eq albumName.bindArg() and (artistName eq artist) }
      .sequence({ it[0] = album }) { it[id] }
      .singleOrNull()
      ?: AlbumTable.insert {
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
  .toQuery()

public object FullMediaView : View(
  query = ViewTestsQuery,
  name = "Full Media",
  forceQuoteName = true
) {
  public val mediaId: ViewColumn<Long> = column(MediaFileTable.id, "FullMedia_MediaId")
  public val mediaTitle: ViewColumn<String> =
    column(MediaFileTable.mediaTitle, "FullMedia_MediaTitle")
  public val albumName: ViewColumn<String> = column(AlbumTable.albumName, "FullMedia_AlbumName")
  public val artistName: ViewColumn<String> = column(ArtistTable.artistName, "FullMedia_ArtistName")
}
